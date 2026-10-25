package com.vknxworks.immichctrl.data.security

import android.content.Context
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.data.lock.LockVault
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import android.hardware.biometrics.BiometricManager.Authenticators as PlatformAuthenticators
import android.hardware.biometrics.BiometricPrompt as PlatformPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentActivity
import com.vknxworks.immichctrl.container
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
import com.vknxworks.immichctrl.data.settings.AppSettings
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What a PIN screen on top of the app is for. */
enum class PinRequest { ENABLE, CHANGE, DISABLE }

/**
 * The app lock (the shared Ctrl series lock): an app PIN of its own, optional fingerprint, and a
 * "Lock again" time. The secrets live in [LockVault]; locking wipes its key from memory.
 */
class AppLock(private val context: Context, private val settings: SettingsRepository, val vault: LockVault) {
    // Starts locked so nothing shows before the first check; screens only honour it while app lock is on.
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    @Volatile private var backgroundedAt: Long? = null
    @Volatile private var startChecked = false
    @Volatile private var expectReturnAt = 0L

    /** The prompt (fingerprint, or the system prompt that can open the phone's screen lock) open right now, if any. */
    @Volatile private var promptSignal: CancellationSignal? = null
    @Volatile private var promptStartedAt = 0L
    @Volatile private var promptGeneration = 0
    /** The open prompt can open the phone's screen-lock page, which takes the app to the background. */
    @Volatile private var promptMayLeave = false

    /**
     * The system prompt is up; its screen-lock page takes the app to the background, which mustn't re-lock.
     * Some phones drop a prompt without ever answering (for example one asked for while the app was still
     * coming back on screen); such a prompt stops counting after a minute, and a new prompt always replaces
     * it, so it can never keep the lock from engaging when you leave.
     */
    private val prompting: Boolean
        get() = promptMayLeave && promptSignal != null && SystemClock.elapsedRealtime() - promptStartedAt < PROMPT_STALE_MS

    /** The current trip away started while the system prompt was up (its screen-lock page). */
    @Volatile private var promptTrip = false

    /** A PIN screen shown on top of the app: turning the lock on or off, or changing the PIN. */
    var request by mutableStateOf<PinRequest?>(null)

    /** Goes up every time the app locks, so the lock screen offers the fingerprint once per lock. */
    var lockCount by mutableIntStateOf(0)
        private set

    /**
     * Goes up every time you come back to the app after really leaving it (not for the system prompt's own
     * screen-lock page), so the lock screen offers the fingerprint again on every return, not just once per lock.
     */
    var returns by mutableIntStateOf(0)
        private set

    /** Set by the app: wipe what the vault key opened. */
    var onLocked: () -> Unit = {}

    /** Set by the app: reopen what the vault key opened. Runs before the app is shown again. */
    var onUnlocking: suspend () -> Unit = {}

    /** Set by an open screen with unsaved input: keeps it before locking closes the screen. */
    var saveBeforeLock: (() -> Unit)? = null

    /** The lock is on: with an app PIN, or still the old phone-screen-lock lock until a PIN is chosen. */
    fun enabled(s: AppSettings = settings.settings.value): Boolean = vault.enabled || s.appLock

    /** Whether the phone has a fingerprint, face or screen lock the app can use. */
    fun canLock(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /** Only class 3 ("strong") biometrics: camera-only face unlock can be fooled by a photo. */
    fun biometricAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * The app itself is opening another app (share sheet, editor, picker, a system confirmation).
     * That trip doesn't count as leaving, so you don't have to unlock again when you come back.
     */
    fun expectReturn() {
        expectReturnAt = SystemClock.elapsedRealtime()
    }

    suspend fun onForeground() {
        val s = settings.current()
        val away = backgroundedAt?.let { SystemClock.elapsedRealtime() - it }
        val exempt = exemptTrip
        val fromPrompt = promptTrip
        backgroundedAt = null
        exemptTrip = false
        promptTrip = false
        expectReturnAt = 0L
        promptMayLeave = false
        if (away != null && !fromPrompt) returns++
        if (!enabled(s)) {
            startChecked = true
            _locked.value = false
            return
        }
        // -1: only when the app restarts.
        val timeout = s.appLockTimeout
        // A trip the app started still locks if you stay away long: after 5 minutes, or the chosen time if longer.
        val limit = if (exempt) maxOf(timeout * 1000L, 5 * 60_000L) else timeout * 1000L
        if (!startChecked || (away != null && timeout >= 0 && away >= limit)) lock()
        startChecked = true
    }

    /** The current trip away was started by the app itself (share sheet, editor, picker …). */
    @Volatile private var exemptTrip = false

    fun onBackground() {
        val now = SystemClock.elapsedRealtime()
        backgroundedAt = now
        promptTrip = prompting
        if (promptTrip) {
            // The system prompt can open the phone's screen-lock page, which counts like a trip the app
            // started: no lock right away, but staying away long still locks when you come back.
            exemptTrip = true
            expectReturnAt = 0L
            return
        }
        // Only right after the app opened something itself; leaving later still counts.
        exemptTrip = expectReturnAt > 0 && now - expectReturnAt < 5_000
        expectReturnAt = 0L
        if (exemptTrip) return
        // "As soon as you leave" locks right away, so the key is gone while the app is in the background.
        val s = settings.settings.value
        if (enabled(s) && !_locked.value && s.appLockTimeout == 0) lock()
    }

    fun lock() {
        saveBeforeLock?.let { runCatching(it) }
        val wasLocked = _locked.value
        _locked.value = true
        request = null
        // Already showing the lock screen (e.g. at start-up): don't ask for the fingerprint twice.
        if (!wasLocked) lockCount++
        vault.close()
        onLocked()
    }

    /** After the PIN or fingerprint opened the vault (or the lock was just turned on or reset). */
    suspend fun unlocked() {
        startChecked = true
        backgroundedAt = null
        runCatching { onUnlocking() }
        _locked.value = false
    }

    /**
     * The old lock (the phone's own screen lock). Only used once, by people updating from it, to prove
     * it's them before they choose an app PIN. With no screen lock on the phone any more there's nothing
     * to confirm with, so it goes straight on.
     */
    fun unlockLegacy(activity: FragmentActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!canLock()) {
            _locked.value = false
            return
        }
        prompt(activity, activity.getString(R.string.lock_fingerprint_title), allowCredential = true) { ok -> if (ok) _locked.value = false }
    }

    /**
     * Starts tracking a new prompt and returns its signal, plus a finish() that is true once, and only for
     * the newest prompt. A prompt that is still marked as open is cancelled and replaced; its late answer
     * (if any) is ignored, so a prompt the phone dropped without answering can't block the next one.
     */
    private fun openPrompt(mayLeave: Boolean): Pair<CancellationSignal, () -> Boolean> {
        promptSignal?.let { runCatching { it.cancel() } }
        val generation = ++promptGeneration
        val signal = CancellationSignal()
        promptSignal = signal
        promptStartedAt = SystemClock.elapsedRealtime()
        promptMayLeave = mayLeave
        val finish = finish@{
            if (generation != promptGeneration || promptSignal !== signal) return@finish false
            promptSignal = null
            promptMayLeave = false
            true
        }
        return signal to finish
    }

    /**
     * Shows the fingerprint prompt for [cipher], a Keystore key that only works after a strong match.
     * [onSuccess] gets the unlocked cipher; [onError] gets a message, or null when cancelled. Asking while
     * another prompt is still marked as open replaces that one.
     */
    fun biometricPrompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        subtitle: String? = null,
        onError: (String?) -> Unit = {},
        onSuccess: (Cipher) -> Unit,
    ) {
        val (signal, finish) = openPrompt(mayLeave = false)
        try {
            val prompt = PlatformPrompt.Builder(activity)
                .setTitle(title)
                .apply { if (subtitle != null) setSubtitle(subtitle) }
                .setAllowedAuthenticators(PlatformAuthenticators.BIOMETRIC_STRONG)
                .setNegativeButton(activity.getString(R.string.lock_use_pin), activity.mainExecutor) { _, _ ->
                    if (finish()) onError(null)
                }
                .build()
            prompt.authenticate(
                PlatformPrompt.CryptoObject(cipher),
                signal,
                activity.mainExecutor,
                object : PlatformPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: PlatformPrompt.AuthenticationResult) {
                        if (!finish()) return
                        val c = result.cryptoObject?.cipher
                        if (c != null) onSuccess(c) else onError(activity.getString(R.string.lock_fingerprint_failed))
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!finish()) return
                        val cancelled = errorCode == PlatformPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == PlatformPrompt.BIOMETRIC_ERROR_CANCELED
                        onError(if (cancelled) null else errString.toString())
                    }
                },
            )
        } catch (e: Exception) {
            if (finish()) onError(e.message ?: activity.getString(R.string.lock_prompt_failed))
        }
    }

    /** Shows the system's fingerprint/face/screen-lock prompt. Replaces a prompt that is still marked as open. */
    fun prompt(activity: FragmentActivity, title: String, allowCredential: Boolean, onResult: (Boolean) -> Unit) {
        // Only the unlock prompt (shown while already locked) can open the screen-lock page.
        val (signal, finish) = openPrompt(mayLeave = allowCredential)
        try {
            val builder = PlatformPrompt.Builder(activity).setTitle(title)
            // Strong (class 3) biometrics only: weak face unlock can be fooled by a photo.
            if (allowCredential) {
                builder.setAllowedAuthenticators(PlatformAuthenticators.BIOMETRIC_STRONG or PlatformAuthenticators.DEVICE_CREDENTIAL)
            } else {
                builder.setAllowedAuthenticators(PlatformAuthenticators.BIOMETRIC_STRONG)
                builder.setNegativeButton(activity.getString(R.string.lock_use_pin), activity.mainExecutor) { _, _ ->
                    if (finish()) onResult(false)
                }
            }
            builder.build().authenticate(
                signal,
                activity.mainExecutor,
                object : PlatformPrompt.AuthenticationCallback() {
                    // backgroundedAt is left alone: onForeground() measures the time away, also for this trip.
                    override fun onAuthenticationSucceeded(result: PlatformPrompt.AuthenticationResult) {
                        if (finish()) onResult(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (finish()) onResult(false)
                    }
                },
            )
        } catch (e: Exception) {
            if (finish()) onResult(false)
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
                .setNegativeButtonText(activity.getString(R.string.lock_use_pin))
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

    private companion object {
        const val PROMPT_STALE_MS = 60_000L
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

/**
 * Call right before the app opens another app itself (share sheet, picker, permission page, map, a
 * system confirmation): coming back from it doesn't ask to unlock again.
 */
fun expectAppReturn(context: Context) {
    runCatching { context.container.appLock.expectReturn() }
}
