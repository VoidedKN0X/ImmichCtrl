package com.vknxworks.immichctrl.data.net

import com.vknxworks.immichctrl.data.api.ApiJson
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.log.AppLog
import com.vknxworks.immichctrl.data.timeline.Sources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Immich's live updates (socket.io over a websocket): new uploads from other devices, deletes,
 * edits and album changes show up without pulling to refresh. Only connected while the app is in
 * the foreground, with a growing delay between reconnects so an unreachable server can't drain the battery.
 */
class LiveUpdates(private val http: OkHttpClient, private val scope: CoroutineScope) {
    private var socket: WebSocket? = null
    private var client: ImmichClient? = null
    private var foreground = false
    private var attempt = 0
    private var reconnectJob: Job? = null
    private var refreshJob: Job? = null

    /** Called with each server-side event name, for anything else that wants to react (admin alerts). */
    var onEvent: ((String) -> Unit)? = null

    fun setClient(c: ImmichClient?) {
        if (c === client) return
        client = c
        restart()
    }

    /** Connects again, e.g. after the user switched between the home and public address. */
    fun reconnect() {
        attempt = 0
        restart()
    }

    fun setForeground(fg: Boolean) {
        foreground = fg
        if (fg) restart() else stop()
    }

    private fun stop() {
        reconnectJob?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    private fun restart() {
        stop()
        val c = client ?: return
        if (!foreground) return
        // OkHttp upgrades an http(s) request to a websocket; the app's interceptors add credentials.
        val req = Request.Builder().url(c.apiUrl + "/socket.io/?EIO=4&transport=websocket").build()
        socket = http.newWebSocket(req, Listener())
    }

    private fun scheduleReconnect() {
        if (!foreground || client == null) return
        reconnectJob?.cancel()
        val wait = (2_000L shl attempt.coerceAtMost(6)).coerceAtMost(120_000L)
        attempt++
        reconnectJob = scope.launch {
            delay(wait)
            restart()
        }
    }

    /** Several events usually arrive together (a batch upload); refresh once. */
    private fun refreshSoon() {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.Main) {
            delay(1500)
            Sources.refreshAll()
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when {
                text.startsWith("0") -> webSocket.send("40") // engine.io open: join the default namespace
                text == "2" -> webSocket.send("3") // ping -> pong
                text.startsWith("40") -> AppLog.i("Live", "Connected to live updates")
                text.startsWith("42") -> handle(text.substring(2))
                text.startsWith("41") -> webSocket.close(1000, null)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket === webSocket) {
                if (attempt == 0) AppLog.w("Live", "Live updates disconnected: ${t.message ?: t::class.simpleName}")
                socket = null
                scheduleReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket === webSocket) {
                socket = null
                scheduleReconnect()
            }
        }
    }

    private fun handle(payload: String) {
        val arr = runCatching { ApiJson.parseToJsonElement(payload) as? JsonArray }.getOrNull() ?: return
        val event = (arr.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: return
        val data = arr.getOrNull(1)
        onEvent?.invoke(event)
        when (event) {
            "on_asset_delete", "on_asset_hidden" -> {
                val id = (data as? JsonPrimitive)?.contentOrNull ?: return
                scope.launch(Dispatchers.Main) { Sources.removeEverywhere(setOf(id)) }
            }
            "on_asset_trash" -> {
                val ids = (data as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()
                scope.launch(Dispatchers.Main) { Sources.removeEverywhere(ids) }
                refreshSoon()
            }
            "on_upload_success", "on_asset_update", "on_asset_restore", "on_asset_stack_update", "on_album_update", "on_person_thumbnail" -> refreshSoon()
        }
    }
}
