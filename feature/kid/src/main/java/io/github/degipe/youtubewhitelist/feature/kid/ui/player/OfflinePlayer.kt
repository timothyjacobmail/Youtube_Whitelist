package io.github.degipe.youtubewhitelist.feature.kid.ui.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun OfflinePlayer(
    videoPath: String,
    audioPath: String?,
    speed: Float,
    onVideoEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onEnded by rememberUpdatedState(onVideoEnded)
    var failed by remember(videoPath) { mutableStateOf(false) }
    val player = remember(videoPath, audioPath) {
        ExoPlayer.Builder(context).build().apply {
            val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
            val video = factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            setMediaSource(if (audioPath == null) video else MergingMediaSource(video,
                factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(File(audioPath))))))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }
                override fun onPlayerError(error: PlaybackException) { failed = true }
            })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    if (failed) {
        Text("Couldn't play this offline copy. Delete it and download the video again.", modifier)
    } else {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            update = { player.setPlaybackSpeed(speed) },
            modifier = modifier
        )
    }
}
