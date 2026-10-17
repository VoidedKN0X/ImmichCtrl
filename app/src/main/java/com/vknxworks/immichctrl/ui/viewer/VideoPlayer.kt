package com.vknxworks.immichctrl.ui.viewer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    videoAssetId: String,
    active: Boolean,
    autoPlay: Boolean,
    loop: Boolean,
    showControls: Boolean,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = context.container
    val player = remember(videoAssetId) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(c.http)))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(c.api.videoUrl(videoAssetId)))
                prepare()
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
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
            view.useController = showControls
            if (showControls) view.showController() else view.hideController()
        },
        modifier = modifier.fillMaxSize(),
    )
}
