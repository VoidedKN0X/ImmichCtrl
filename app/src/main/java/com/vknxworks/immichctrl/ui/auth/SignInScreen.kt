package com.vknxworks.immichctrl.ui.auth

import com.vknxworks.immichctrl.data.net.parseHeaders
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.auth.HttpFallbackNeeded
import com.vknxworks.immichctrl.data.auth.ServerProbe
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.friendlyMessage
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var probe by remember { mutableStateOf<ServerProbe?>(null) }
    var headerText by remember { mutableStateOf("") } // may hold proxy secrets: not kept in saved state
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var useApiKey by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offerHttp by remember { mutableStateOf<String?>(null) }
    var waitingForOAuth by remember { mutableStateOf(false) }

    val active by c.sessions.active.collectAsStateWithLifecycle()
    val startKey = remember { c.sessions.active.value?.account?.key }
    val oauthError by c.sessions.oauthError.collectAsStateWithLifecycle()

    // OAuth finishes in the background when the browser hands back to the app.
    LaunchedEffect(active?.account?.key) {
        if (waitingForOAuth && active != null && active?.account?.key != startKey) {
            waitingForOAuth = false
            onDone()
        }
    }
    LaunchedEffect(oauthError) {
        if (oauthError != null) {
            waitingForOAuth = false
            error = oauthError
            c.sessions.clearOAuthError()
        }
    }

    fun findServer(target: String) {
        busy = true
        error = null
        scope.launch {
            try {
                val p = c.sessions.probe(target, parseHeaders(headerText))
                probe = p
                address = p.baseUrl
                if (!p.features.passwordLogin && !p.features.oauth) useApiKey = true
            } catch (e: HttpFallbackNeeded) {
                offerHttp = e.httpUrl
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                busy = false
            }
        }
    }

    fun signIn() {
        val p = probe ?: return
        busy = true
        error = null
        scope.launch {
            try {
                if (useApiKey) c.sessions.signInWithApiKey(p, apiKey) else c.sessions.signInWithPassword(p, email, password)
                onDone()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                busy = false
            }
        }
    }

    fun oauth() {
        val p = probe ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val url = c.sessions.startOAuth(p)
                waitingForOAuth = true
                com.vknxworks.immichctrl.data.security.expectAppReturn(context)
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                busy = false
            }
        }
    }

    offerHttp?.let { httpUrl ->
        ConfirmDialog(
            "Use an unencrypted connection?",
            "ImmichCtrl couldn't connect to this server over HTTPS. It can try plain HTTP instead, " +
                "but then your password and photos are sent unencrypted and anyone on the network can see them. " +
                "Only do this on a network you trust.",
            "Use HTTP",
            onDismiss = {
                offerHttp = null
                error = "Couldn't connect securely (HTTPS)"
            },
        ) {
            offerHttp = null
            address = httpUrl
            findServer(httpUrl)
        }
    }

    BackHandler(enabled = probe != null || onCancel != null) {
        if (probe != null) {
            probe = null
            error = null
        } else {
            onCancel?.invoke()
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.height(48.dp)) {
                if (onCancel != null || probe != null) {
                    IconButton(onClick = { if (probe != null) probe = null else onCancel?.invoke() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            }
            Text(if (onCancel == null) "ImmichCtrl" else "Add an account", style = MaterialTheme.typography.displaySmall)

            val p = probe
            if (p == null) {
                Text(
                    "Sign in to your Immich server with the address you'd open in a browser.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Field(
                    address, { address = it },
                    label = "Server address",
                    placeholder = "photos.example.com",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    actions = KeyboardActions(onGo = { if (address.isNotBlank()) findServer(address) }),
                    enabled = !busy,
                )
                HttpWarning(address)
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Hide advanced" else if (headerText.isBlank()) "Advanced: custom headers" else "Advanced: ${parseHeaders(headerText).size} custom headers")
                }
                if (showAdvanced) {
                    TextField(
                        value = headerText,
                        onValueChange = { headerText = it },
                        label = { Text("Headers, one per line") },
                        placeholder = { Text("CF-Access-Client-Id: …") },
                        minLines = 2,
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Sent with every request, for servers behind Cloudflare Access, an auth proxy and the like. Change them later in Settings → Networking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (busy) Spinner() else PillButton("Next", onClick = { findServer(address) }, enabled = address.isNotBlank(), modifier = Modifier.fillMaxWidth())
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.baseUrl, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull("Immich", p.version).joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { probe = null }) { Text("Change") }
                }
                HttpWarning(p.baseUrl)
                if (p.config.loginPageMessage.isNotBlank()) {
                    Text(p.config.loginPageMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (useApiKey) {
                    Field(
                        apiKey, { apiKey = it },
                        label = "API key",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        actions = KeyboardActions(onDone = { if (apiKey.isNotBlank()) signIn() }),
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        enabled = !busy,
                        trailing = { ShowHide(showPassword) { showPassword = !showPassword } },
                    )
                    Text(
                        "Create one in Immich under Account settings > API keys. Give it full access so every feature here works.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (p.features.passwordLogin) {
                    Field(
                        email, { email = it },
                        label = "Email",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        enabled = !busy,
                    )
                    Field(
                        password, { password = it },
                        label = "Password",
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        actions = KeyboardActions(onDone = { if (email.isNotBlank() && password.isNotEmpty()) signIn() }),
                        enabled = !busy,
                        trailing = { ShowHide(showPassword) { showPassword = !showPassword } },
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (busy || waitingForOAuth) {
                    Spinner()
                    if (waitingForOAuth) {
                        Text("Finish signing in in your browser.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { waitingForOAuth = false }) { Text("Cancel") }
                    }
                } else {
                    if (useApiKey || p.features.passwordLogin) {
                        PillButton(
                            "Sign in",
                            onClick = ::signIn,
                            enabled = if (useApiKey) apiKey.isNotBlank() else email.isNotBlank() && password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (p.features.oauth && !useApiKey) {
                        OutlinedButton(onClick = ::oauth, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(p.config.oauthButtonText.ifBlank { "Sign in with OAuth" }, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    TextButton(onClick = { useApiKey = !useApiKey; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Filled.Key, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (useApiKey) "Sign in with your account instead" else "Use an API key instead")
                    }
                }
                Text(
                    "Your sign-in is stored encrypted on this phone and only sent to your server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
}

@Composable
private fun ShowHide(shown: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (shown) "Hide" else "Show")
    }
}

@Composable
private fun HttpWarning(address: String) {
    if (!address.trim().startsWith("http://", ignoreCase = true)) return
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LockOpen, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Text(
            "Not encrypted (HTTP). Your password and photos are sent in the clear. Fine on your home network, risky anywhere else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboard,
        keyboardActions = actions,
        visualTransformation = visual,
        trailingIcon = trailing,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
