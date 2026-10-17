package com.vknxworks.immichctrl.data.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentActivity
import com.vknxworks.immichctrl.data.appDataStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Locks the app behind the phone's fingerprint, face or screen lock when it opens, and again after
 * it has been in the background for the chosen time.
 */
class AppLock(private val context: Context, private val settings: SettingsRepository) {
    // Starts locked so nothing shows before the first check; screens only honour it while app lock is on.
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    @Volatile private var backgroundedAt: Long? = null
    @Volatile private var startChecked = false
    /** The system prompt is up; its screen-lock page takes the app to the background, which mustn't re-lock. */
    @Volatile private var prompting = false

    /** Whether the phone has a fingerprint, face or screen lock the app can use. */
    fun canLock(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun onForeground() {
        val s = settings.current()
        if (!s.appLock) {
            _locked.value = false
            return
        }
        val away = backgroundedAt?.let { SystemClock.elapsedRealtime() - it }
        // A few seconds' grace, so system dialogs (delete confirmations, the photo picker) don't lock the app.
        if (!startChecked || (away != null && away >= maxOf(s.appLockTimeout * 1000L, 5_000L))) _locked.value = true
        startChecked = true
        backgroundedAt = null
        prompting = false
    }

    /** After the user has just proved it's them (e.g. turning app lock on). */
    fun markUnlocked() {
        _locked.value = false
        startChecked = true
    }

    fun onBackground() {
        if (prompting) return
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun unlockWith(activity: FragmentActivity) {
        // The phone's screen lock was removed: there's nothing to unlock with, so don't trap the user.
        if (!canLock()) {
            _locked.value = false
            return
        }
        prompt(activity, "Unlock ImmichCtrl", allowCredential = true) { ok -> if (ok) _locked.value = false }
    }

    /** Shows the system's fingerprint/face/screen-lock prompt. */
    fun prompt(activity: FragmentActivity, title: String, allowCredential: Boolean, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    backgroundedAt = null
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    backgroundedAt = null
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                // Strong (class 3) biometrics only: weak face unlock can be fooled by a photo.
                if (allowCredential) setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                else {
                    setAllowedAuthenticators(BIOMETRIC_STRONG)
                    setNegativeButtonText("Use PIN")
                }
            }
            .build()
        // Only the unlock prompt (shown while already locked) can open the screen-lock page.
        prompting = allowCredential
        runCatching { prompt.authenticate(info) }.onFailure {
            prompting = false
            onResult(false)
        }
    }

    // ---------------- Locked folder PIN, remembered for fingerprint unlock ----------------

    /** Whether the phone has a strong (class 3) fingerprint or face unlock, which the saved PIN needs. */
    fun canUseBiometricPin(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun pinKey(account: String) = stringPreferencesKey("locked_pin2:$account")
    /** Older versions kept the PIN under a key that didn't need the fingerprint to read. */
    private fun legacyPinKey(account: String) = stringPreferencesKey("locked_pin:$account")

    /**
     * Remembers the locked-folder PIN. It's encrypted with the public half of a Keystore key pair whose
     * private half can only be used right after a strong fingerprint/face check (and is wiped when a
     * new fingerprint is enrolled), so reading it back always needs the user.
     */
    suspend fun savePin(account: String, pin: String) {
        val enc = runCatching { BiometricPinCrypto.encrypt(pin) }.getOrNull() ?: return
        context.appDataStore.edit {
            it[pinKey(account)] = enc
            it.remove(legacyPinKey(account))
        }
    }

    suspend fun hasSavedPin(account: String): Boolean {
        val prefs = context.appDataStore.data.first()
        if (prefs[legacyPinKey(account)] != null) context.appDataStore.edit { it.remove(legacyPinKey(account)) }
        return prefs[pinKey(account)] != null
    }

    /**
     * Asks for a strong fingerprint/face check and decrypts the saved PIN with the key it unlocks.
     * Null when cancelled, unavailable, or the key was invalidated (then the saved PIN is forgotten).
     */
    suspend fun unlockSavedPin(activity: FragmentActivity, account: String, title: String): String? {
        val enc = context.appDataStore.data.first()[pinKey(account)] ?: return null
        val cipher = try {
            BiometricPinCrypto.decryptCipher()
        } catch (e: Exception) {
            // New fingerprints were added or the screen lock was removed: the key is gone for good.
            forgetPin(account)
            return null
        }
        val unlocked = suspendCancellableCoroutine<javax.crypto.Cipher?> { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(result.cryptoObject?.cipher)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN")
                .build()
            runCatching { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }
                .onFailure { if (cont.isActive) cont.resume(null) }
        } ?: return null
        return runCatching { BiometricPinCrypto.decrypt(unlocked, enc) }.getOrNull()
    }

    suspend fun forgetPin(account: String) {
        context.appDataStore.edit {
            it.remove(pinKey(account))
            it.remove(legacyPinKey(account))
        }
    }
}

/**
 * RSA-OAEP key pair in the Android Keystore for the remembered locked-folder PIN. Encrypting uses the
 * public key and needs nothing; decrypting needs the private key, which the Keystore only releases
 * for one operation after a strong biometric check (BiometricPrompt with a CryptoObject).
 */
private object BiometricPinCrypto {
    private const val ALIAS = "immichctrl_locked_pin"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private val oaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun ensureKey(ks: KeyStore) {
        if (ks.containsAlias(ALIAS)) return
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER)
        gen.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        gen.generateKeyPair()
    }

    fun encrypt(plain: String): String {
        val ks = keyStore()
        ensureKey(ks)
        // The public key works outside the Keystore; copying it avoids Keystore restrictions on it.
        val stored = ks.getCertificate(ALIAS).publicKey
        val public = KeyFactory.getInstance(stored.algorithm).generatePublic(X509EncodedKeySpec(stored.encoded))
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, public, oaep)
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** A cipher ready to decrypt once BiometricPrompt has unlocked it. Throws if the key is gone. */
    fun decryptCipher(): Cipher {
        val key = keyStore().getKey(ALIAS, null) as? PrivateKey ?: throw IllegalStateException("No key")
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key, oaep) }
    }

    fun decrypt(cipher: Cipher, encoded: String): String =
        String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
}
