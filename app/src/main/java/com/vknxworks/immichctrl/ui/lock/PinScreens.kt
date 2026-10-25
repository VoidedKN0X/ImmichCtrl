package com.vknxworks.immichctrl.ui.lock

import com.vknxworks.immichctrl.ui.theme.CtrlType
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.components.CtrlLabel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.lock.LockVault
import com.vknxworks.immichctrl.data.security.PinRequest
import com.vknxworks.immichctrl.data.security.SecureScreen
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.R

/**
 * Waits until the app is really back on screen: resumed, with its window focused. A prompt asked for earlier
 * (while the app is still coming back from the background) is dropped by the system, on some phones without
 * any answer, which used to leave fingerprint unlock not working until the app was closed.
 */
private suspend fun awaitOnScreen(lifecycle: Lifecycle, windowInfo: WindowInfo) {
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    snapshotFlow { windowInfo.isWindowFocused }.first { it }
}

/** Updated from the old app lock (the phone's screen lock): confirm once, then choose a PIN. */
@Composable
fun LegacyLockScreen() {
    val context = LocalContext.current
    val c = context.container
    fun unlock() { context.findActivity()?.let { c.appLock.unlockLegacy(it) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowInfo = LocalWindowInfo.current
    // Asked on every lock and every time you come back, once the app is really on screen.
    LaunchedEffect(c.appLock.lockCount, c.appLock.returns) {
        awaitOnScreen(lifecycle, windowInfo)
        unlock()
    }
    LockCover {
        Column(
            Modifier.widthIn(max = 360.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LockBadge()
            CtrlLabel("VKNXWorks · Ctrl", Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.lock_legacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PillButton(stringResource(R.string.lock_unlock), onClick = ::unlock)
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
        Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Accent),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Lock, null, Modifier.size(30.dp), tint = CtrlColors.OnAccent) }
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
    val bioAvailable = remember(lock.lockCount, lock.returns) { vault.biometricEnabled && lock.biometricAvailable() && !vault.pinRequired() }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    fun askFingerprint() {
        val a = activity ?: return
        val cipher = vault.biometricUnlockCipher() ?: run {
            if (!vault.biometricEnabled) message = context.getString(R.string.lock_fingerprint_changed)
            return
        }
        lock.biometricPrompt(a, context.getString(R.string.lock_fingerprint_title), cipher, subtitle = context.getString(R.string.lock_fingerprint_subtitle), onError = { message = it }) { unlocked ->
            if (vault.unlockWithBiometric(unlocked)) scope.launch { lock.unlocked() }
            else message = context.getString(R.string.lock_fingerprint_failed)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowInfo = LocalWindowInfo.current
    // Offered on every lock and every time you come back to the lock screen, once the app is really on screen.
    LaunchedEffect(lock.lockCount, lock.returns) {
        if (!bioAvailable) return@LaunchedEffect
        awaitOnScreen(lifecycle, windowInfo)
        askFingerprint()
    }

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
                message = context.getString(R.string.lock_wrong_pin)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
            }
        }
    }

    LockCover {
        PinPad(
            title = stringResource(R.string.lock_title),
            message = when {
                lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
                message != null -> message!!
                vault.biometricEnabled && vault.pinRequired() -> stringResource(R.string.lock_pin_due)
                else -> stringResource(R.string.lock_enter_pin)
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
            onFingerprint = if (bioAvailable) ({ askFingerprint() }) else null,
        ) {
            TextButton(onClick = { forgetting = true }) { Text(stringResource(R.string.lock_forgot), color = CtrlColors.Muted) }
        }
    }
    if (forgetting) {
        ConfirmDialog(
            stringResource(R.string.lock_forgot_title),
            stringResource(R.string.lock_forgot_body),
            stringResource(R.string.lock_forgot_confirm),
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
            title = stringResource(R.string.lock_choose_title),
            intro = stringResource(R.string.lock_choose_intro, LockVault.PIN_MIN),
            verifyCurrent = false,
            cancellable = true,
            onDone = { pin -> c.enableLock(pin); lock.request = null },
            onCancel = { lock.request = null },
        )
        PinRequest.CHANGE -> PinSetupScreen(
            title = stringResource(R.string.lock_change_title),
            intro = stringResource(R.string.lock_change_intro, LockVault.PIN_MIN),
            verifyCurrent = true,
            cancellable = true,
            onDone = { pin -> withContext(Dispatchers.Default) { c.vault.changePin(pin) }; lock.request = null },
            onCancel = { lock.request = null },
        )
        PinRequest.DISABLE -> PinSetupScreen(
            title = stringResource(R.string.lock_disable_title),
            intro = stringResource(R.string.lock_disable_intro),
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
        title = stringResource(R.string.lock_choose_title),
        intro = stringResource(R.string.lock_migrate_intro),
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
            runCatching { onDone(newPin) }.onFailure { busy = false; wrong(context.getString(R.string.lock_save_failed)) }
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
                        wrong(context.getString(R.string.lock_wrong_pin))
                    }
                }
            }
            1 -> {
                if (pin.length < LockVault.PIN_MIN) return
                if (pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)) {
                    wrong(context.getString(R.string.lock_too_easy))
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
                    wrong(context.getString(R.string.lock_mismatch))
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
                lockout > 0 -> stringResource(R.string.lock_too_many, ((lockout + 999) / 1000).toInt())
                message != null -> message!!
                step == 0 -> stringResource(R.string.lock_current_pin)
                step == 1 -> intro
                else -> stringResource(R.string.lock_repeat_pin)
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
            confirmLabel = stringResource(if (step == 2 || (step == 0 && verifyOnly)) R.string.lock_done else R.string.lock_next),
        ) {
            if (cancellable) TextButton(onClick = onCancel) { Text(stringResource(R.string.lock_cancel), color = CtrlColors.Muted) }
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
    confirmLabel: String = stringResource(R.string.lock_ok),
    onFingerprint: (() -> Unit)? = null,
    bottom: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    fun digit(d: Char) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onDigit(d)
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        LockBadge()
        Spacer(Modifier.height(28.dp))
        CtrlLabel("VKNXWorks · Ctrl")
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.035).em),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) CtrlColors.Error else CtrlColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.height(20.dp).offset { IntOffset(shake.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
            if (checking) CircularProgressIndicator(Modifier.size(20.dp), color = CtrlColors.AccentText, strokeWidth = 2.dp)
            else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(maxOf(pin.length, LockVault.PIN_MIN)) { i ->
                    val shape = RoundedCornerShape(5.dp)
                    Box(
                        Modifier.size(14.dp).clip(shape)
                            .then(if (i < pin.length) Modifier.background(CtrlColors.Accent) else Modifier.border(2.dp, CtrlColors.PinEmpty, shape)),
                    )
                }
            }
        }
        Spacer(Modifier.height(44.dp))
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { d -> DigitKey(d.toString(), enabled, Modifier.weight(1f)) { digit(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Left: fingerprint when it's on, otherwise OK / Next once enough digits are in.
                val keyMod = Modifier.weight(1f)
                when {
                    onFingerprint != null && !showConfirm -> PadKey(
                        keyMod,
                        enabled = true,
                        border = CtrlColors.AccentText,
                        container = Color.Transparent,
                        label = stringResource(R.string.lock_use_fingerprint),
                        onClick = onFingerprint,
                    ) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(26.dp), tint = CtrlColors.AccentText) }
                    else -> PadKey(
                        keyMod,
                        enabled = enabled && showConfirm,
                        border = if (enabled && showConfirm) CtrlColors.Accent else Color.Transparent,
                        container = if (enabled && showConfirm) CtrlColors.Accent else Color.Transparent,
                        label = confirmLabel,
                        onClick = onConfirm,
                    ) {
                        if (showConfirm) Text(confirmLabel.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold), color = CtrlColors.OnAccent, maxLines = 1)
                    }
                }
                DigitKey("0", enabled, Modifier.weight(1f)) { digit('0') }
                PadKey(
                    Modifier.weight(1f),
                    enabled = enabled && pin.isNotEmpty(),
                    border = Color.Transparent,
                    container = Color.Transparent,
                    label = stringResource(R.string.lock_delete),
                    onClick = onBackspace,
                ) { Icon(Icons.AutoMirrored.Outlined.Backspace, null, Modifier.size(24.dp), tint = CtrlColors.Muted) }
            }
            // Both a fingerprint and an OK key: OK gets its own row so neither goes missing.
            if (onFingerprint != null && showConfirm) {
                PillButton(confirmLabel, onClick = onConfirm, enabled = enabled, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(16.dp))
        bottom()
    }
}

/** A keypad key: 68dp tall, 22dp corners. */
@Composable
private fun PadKey(
    modifier: Modifier,
    enabled: Boolean,
    border: Color,
    container: Color,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(68.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(68.dp)
            .clip(shape)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = CtrlType.Mono.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
            color = if (enabled) CtrlColors.Text else CtrlColors.Stopped,
        )
    }
}
