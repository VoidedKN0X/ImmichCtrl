package com.vknxworks.immichctrl.data.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentActivity
import com.vknxworks.immichctrl.data.appDataStore
import com.vknxworks.immichctrl.data.auth.KeystoreCrypto
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
    private var backgroundedAt: Long? = null
    private var startChecked = false

    /** Whether the phone has a fingerprint, face or screen lock the app can use. */
    fun canLock(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

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
    }

    /** After the user has just proved it's them (e.g. turning app lock on). */
    fun markUnlocked() {
        _locked.value = false
        startChecked = true
    }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun unlockWith(activity: FragmentActivity) {
        prompt(activity, "Unlock ImmichCtrl", allowCredential = true) { ok -> if (ok) _locked.value = false }
    }

    /** Shows the system's fingerprint/face/screen-lock prompt. */
    fun prompt(activity: FragmentActivity, title: String, allowCredential: Boolean, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (allowCredential) setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                else {
                    setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
                    setNegativeButtonText("Use PIN")
                }
            }
            .build()
        prompt.authenticate(info)
    }

    suspend fun promptAsync(activity: FragmentActivity, title: String): Boolean = suspendCancellableCoroutine { cont ->
        prompt(activity, title, allowCredential = false) { ok -> if (cont.isActive) cont.resume(ok) }
    }

    // ---------------- Locked folder PIN, remembered for fingerprint unlock ----------------

    private fun pinKey(account: String) = stringPreferencesKey("locked_pin:$account")

    suspend fun savePin(account: String, pin: String) {
        context.appDataStore.edit { it[pinKey(account)] = KeystoreCrypto.encrypt(pin) }
    }

    suspend fun savedPin(account: String): String? =
        context.appDataStore.data.first()[pinKey(account)]?.let { KeystoreCrypto.decrypt(it) }

    suspend fun forgetPin(account: String) {
        context.appDataStore.edit { it.remove(pinKey(account)) }
    }
}
