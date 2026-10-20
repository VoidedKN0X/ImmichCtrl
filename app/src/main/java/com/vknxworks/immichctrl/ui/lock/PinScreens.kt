package com.vknxworks.immichctrl.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.security.LockVault
import com.vknxworks.immichctrl.data.security.PinRequest
import com.vknxworks.immichctrl.data.security.SecureScreen
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Updated from the old app lock (the phone's screen lock): confirm once, then choose a PIN. */
@Composable
fun LegacyLockScreen() {
    val context = LocalContext.current
    val c = context.container
    fun unlock() { context.findActivity()?.let { c.appLock.unlockLegacy(it) } }
    LaunchedEffect(c.appLock.lockCount) { unlock() }
    LockCover {
        Column(
            Modifier.widthIn(max = 360.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LockBadge()
            Text("ImmichCtrl is locked", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "Unlock with your fingerprint, face or screen lock one last time, then choose an app PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PillButton("Unlock", onClick = ::unlock)
        }
    }
}

/** Covers the app, keeps it out of screenshots and swallows touches. */
@Composable
private fun LockCover(content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity()
    // Nothing behind the cover may end up in a screenshot or screen recording.
    SecureScreen()
    BackHandler { activity?.moveTaskToBack(true) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LockBadge() {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?". */
@Composable
fun PinLockScreen() {
    val context = LocalContext.current
    val c = context.container
    val lock = c.appLock
    val vault = c.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }
    var forgetting by remember { mutableStateOf(false) }
    val bioAvailable = remember(lock.lockCount) { vault.biometricEnabled && lock.biometricAvailable() && !vault.pinRequired() }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    fun askFingerprint() {
        val a = activity ?: return
        val cipher = vault.biometricUnlockCipher() ?: run {
            if (!vault.biometricEnabled) message = "A fingerprint was added or removed. Unlock with your PIN, then turn fingerprint unlock on again."
            return
        }
        lock.biometricPrompt(a, "Unlock ImmichCtrl", cipher, onError = { message = it }) { unlocked ->
            if (vault.unlockWithBiometric(unlocked)) scope.launch { lock.unlocked() }
            else message = "Couldn't unlock with your fingerprint. Use your PIN."
        }
    }
    LaunchedEffect(lock.lockCount) { if (bioAvailable) askFingerprint() }

    fun submit() {
        if (checking || lockout > 0 || pin.length < LockVault.PIN_MIN) return
        val entered = pin
        checking = true
        scope.launch {
            // Slow on purpose (it makes guessing slow too), so off the main thread.
            val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
            pin = ""
            if (result == LockVault.PinResult.Ok) {
                lock.unlocked()
                checking = false
            } else {
                checking = false
                lockout = vault.lockoutRemaining()
                message = "Wrong PIN"
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
            }
        }
    }

    LockCover {
        PinPad(
            title = "ImmichCtrl is locked",
            message = when {
                lockout > 0 -> "Too many tries. Try again in ${(lockout + 999) / 1000} s"
                message != null -> message!!
                vault.biometricEnabled && vault.pinRequired() -> "Enter your PIN. It's needed after a restart and every 72 hours."
                else -> "Enter your PIN"
            },
            isError = lockout > 0 || message != null,
            pin = pin,
            shake = shake.value,
            checking = checking,
            enabled = lockout == 0L && !checking,
            onDigit = { d ->
                if (pin.length < LockVault.PIN_MAX) {
                    message = null
                    pin += d
                    if (pin.length == vault.pinLength) submit()
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            onConfirm = ::submit,
            showConfirm = pin.length >= LockVault.PIN_MIN && pin.length != vault.pinLength,
        ) {
            if (bioAvailable) {
                TextButton(onClick = ::askFingerprint) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint")
                }
            }
            TextButton(onClick = { forgetting = true }) { Text("Forgot PIN?") }
        }
    }
    if (forgetting) {
        ConfirmDialog(
            "Forgot your PIN?",
            "The PIN can't be recovered, because it encrypts your login. ImmichCtrl will sign out of every account and " +
                "remove its offline albums, cached photos and backup history from this phone. Your photos on the Immich server " +
                "and the photos on this phone stay.",
            "Sign out and reset",
            onDismiss = { forgetting = false },
        ) {
            forgetting = false
            scope.launch { c.forgetEverything() }
        }
    }
}

/** Shows the PIN screen asked for in [com.vknxworks.immichctrl.data.security.AppLock.request], if any. */
@Composable
fun PinRequestScreen(request: PinRequest) {
    val c = LocalContext.current.container
    val lock = c.appLock
    when (request) {
        PinRequest.ENABLE -> PinSetupScreen(
            title = "Choose an app PIN",
            intro = "At least ${LockVault.PIN_MIN} digits, different from your phone's PIN. " +
                "If you forget it, the only way back in is to sign out and sign in again.",
            verifyCurrent = false,
            cancellable = true,
            onDone = { pin -> c.enableLock(pin); lock.request = null },
            onCancel = { lock.request = null },
        )
        PinRequest.CHANGE -> PinSetupScreen(
            title = "Change app PIN",
            intro = "At least ${LockVault.PIN_MIN} digits, different from your phone's PIN.",
            verifyCurrent = true,
            cancellable = true,
            onDone = { pin -> withContext(Dispatchers.Default) { c.vault.changePin(pin) }; lock.request = null },
            onCancel = { lock.request = null },
        )
        PinRequest.DISABLE -> PinSetupScreen(
            title = "Turn off app lock",
            intro = "Enter your app PIN to turn the lock off.",
            verifyCurrent = true,
            verifyOnly = true,
            cancellable = true,
            onDone = { c.disableLock(); lock.request = null },
            onCancel = { lock.request = null },
        )
    }
}

/** Shown once after updating from the old app lock: a PIN has to be chosen before going on. */
@Composable
fun MandatoryPinScreen() {
    val c = LocalContext.current.container
    PinSetupScreen(
        title = "Choose an app PIN",
        intro = "ImmichCtrl's app lock now has its own PIN, separate from your phone's. " +
            "Your login is encrypted with it while the app is locked.",
        verifyCurrent = false,
        cancellable = false,
        onDone = { pin -> c.enableLock(pin) },
        onCancel = {},
    )
}

/** Choosing a PIN (twice), changing it (current, then new twice) or confirming it to turn the lock off. */
@Composable
private fun PinSetupScreen(
    title: String,
    intro: String,
    verifyCurrent: Boolean,
    cancellable: Boolean,
    onDone: suspend (String) -> Unit,
    onCancel: () -> Unit,
    verifyOnly: Boolean = false,
) {
    val context = LocalContext.current
    val vault = context.container.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    // 0 = current PIN, 1 = new PIN, 2 = repeat new PIN
    var step by remember { mutableIntStateOf(if (verifyCurrent) 0 else 1) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    BackHandler { if (cancellable) onCancel() else activity?.moveTaskToBack(true) }

    fun wrong(text: String) {
        message = text
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45)) }
    }

    fun finish(newPin: String) {
        busy = true
        scope.launch {
            runCatching { onDone(newPin) }.onFailure { busy = false; wrong("Couldn't save the PIN. Try again.") }
        }
    }

    fun next() {
        if (busy || lockout > 0) return
        when (step) {
            0 -> {
                if (pin.length < LockVault.PIN_MIN) return
                val entered = pin
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
                    busy = false
                    if (result == LockVault.PinResult.Ok) {
                        pin = ""
                        message = null
                        if (verifyOnly) finish(entered) else step = 1
                    } else {
                        lockout = vault.lockoutRemaining()
                        wrong("Wrong PIN")
                    }
                }
            }
            1 -> {
                if (pin.length < LockVault.PIN_MIN) return
                if (pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)) {
                    wrong("Too easy to guess. Try a less obvious PIN.")
                    return
                }
                first = pin
                pin = ""
                message = null
                step = 2
            }
            else -> {
                if (pin != first) {
                    first = ""
                    step = 1
                    wrong("The PINs didn't match. Start again.")
                    return
                }
                finish(pin)
            }
        }
    }

    LockCover {
        PinPad(
            title = title,
            message = when {
                lockout > 0 -> "Too many tries. Try again in ${(lockout + 999) / 1000} s"
                message != null -> message!!
                step == 0 -> "Enter your current PIN"
                step == 1 -> intro
                else -> "Enter the same PIN again"
            },
            isError = lockout > 0 || message != null,
            pin = pin,
            shake = shake.value,
            checking = busy,
            enabled = !busy && lockout == 0L,
            onDigit = { d -> if (pin.length < LockVault.PIN_MAX) { message = null; pin += d } },
            onBackspace = { pin = pin.dropLast(1) },
            onConfirm = ::next,
            showConfirm = pin.length >= LockVault.PIN_MIN,
            confirmLabel = if (step == 2 || (step == 0 && verifyOnly)) "Done" else "Next",
        ) {
            if (cancellable) TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun PinPad(
    title: String,
    message: String,
    isError: Boolean,
    pin: String,
    shake: Float,
    checking: Boolean,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    showConfirm: Boolean,
    confirmLabel: String = "OK",
    bottom: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LockBadge()
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) cs.error else cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.height(20.dp).offset { IntOffset(shake.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
            if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(pin.length, LockVault.PIN_MIN)) { i ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) cs.primary else cs.surfaceContainerHighest))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { d ->
                        DigitKey(d.toString(), enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigit(d)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackspace, enabled = enabled && pin.isNotEmpty(), modifier = Modifier.size(72.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
                }
                DigitKey("0", enabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigit('0')
                }
                TextButton(onClick = onConfirm, enabled = enabled && showConfirm, modifier = Modifier.size(72.dp)) { Text(confirmLabel) }
            }
        }
        Spacer(Modifier.height(16.dp))
        bottom()
    }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(72.dp),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
}
