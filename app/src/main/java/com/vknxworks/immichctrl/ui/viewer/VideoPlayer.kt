package com.vknxworks.immichctrl.ui.viewer

import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vknxworks.immichctrl.container

/**
 * Plays an asset's video (or a live photo's motion part) through the app's HTTP client, so the
 * account's credentials go along. Only the page on screen plays; the player is released when it leaves.
 */
@Composable
fun VideoPlayer(
    /** Server asset id of the video, or a content:// / file:// address when [isUri]. */
    videoAssetId: String,
    active: Boolean,
    autoPlay: Boolean,
    loop: Boolean,
    showControls: Boolean,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    isUri: Boolean = false,
    onTap: (() -> Unit)? = null,
    onEnded: (() -> Unit)? = null,
    /**
     * When set, the viewer draws its own controls (VideoControls) instead of the player's built-in
     * ones: called with (player, true) while this page is the one on screen, (player, false) when not.
     */
    onPlayer: ((Player, Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = context.container
    val player = remember(videoAssetId) {
        ExoPlayer.Builder(context)
            // Server videos go through the app's HTTP client (credentials); phone and offline files are read directly.
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(c.http))))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(if (isUri) videoAssetId else c.api.videoUrl(videoAssetId)))
                prepare()
            }
    }
    val ended by rememberUpdatedState(onEnded)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended?.invoke()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    val register by rememberUpdatedState(onPlayer)
    DisposableEffect(player, active) {
        if (active) register?.invoke(player, true)
        onDispose { register?.invoke(player, false) }
    }
    LaunchedEffect(loop) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(active) {
        if (active) {
            if (autoPlay) player.play()
        } else {
            player.pause()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (onTap != null) setOnClickListener { onTap() }
            }
        },
        update = { view ->
            view.player = player
            val builtIn = showControls && onPlayer == null
            view.useController = builtIn
            if (builtIn) view.showController() else view.hideController()
        },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Play/pause, position and a seekbar for the video on screen. Sits above the viewer's island so
 * the two never overlap.
 */
@Composable
fun VideoControls(player: Player, modifier: Modifier = Modifier) {
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var dragging by remember(player) { mutableStateOf<Float?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { duration = player.duration.coerceAtLeast(0L) }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            if (duration <= 0L) duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (player.isPlaying) player.pause()
            else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
        }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play")
        }
        Text(clock(if (dragging != null) (fraction * duration).toLong() else position), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(clock(duration), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
