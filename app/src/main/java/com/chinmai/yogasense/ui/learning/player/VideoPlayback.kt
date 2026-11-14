package com.chinmai.yogasense.ui.learning.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bundled-MP4 playback via Media3 ExoPlayer ("asset:///…"). One instance per screen;
 * [release] frees the decoder and must be called when the player leaves composition.
 */
class VideoPlayback(context: Context, assetPath: String, autoPlay: Boolean, loop: Boolean) : DemoPlayback {
    private val _state = MutableStateFlow(PlaybackState(PlaybackStatus.Loading, isPlaying = false))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        volume = 0f // demonstrations are silent; avoids taking audio focus
        // Built with Uri.Builder so file names with spaces (e.g. "Vrukshasana-Tree pose.mp4") are encoded correctly.
        setMediaItem(MediaItem.fromUri(android.net.Uri.Builder().scheme("asset").authority("").path("/$assetPath").build()))
        playWhenReady = autoPlay
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = sync()
            override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    _state.update { it.copy(videoAspect = aspect) }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(status = PlaybackStatus.Error, isPlaying = false, error = error.errorCodeName) }
            }
        })
        prepare()
    }

    /** Refresh position; called periodically by the UI while playing. */
    fun sync() {
        if (_state.value.status == PlaybackStatus.Released || _state.value.status == PlaybackStatus.Error) return
        val status = when (player.playbackState) {
            Player.STATE_BUFFERING, Player.STATE_IDLE -> PlaybackStatus.Loading
            Player.STATE_ENDED -> PlaybackStatus.Ended
            else -> PlaybackStatus.Ready
        }
        _state.update {
            it.copy(
                status = status,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
            )
        }
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun replay() {
        player.seekTo(0)
        player.play()
    }

    @OptIn(UnstableApi::class)
    override fun release() {
        if (_state.value.status == PlaybackStatus.Released) return
        player.release()
        _state.update { it.copy(status = PlaybackStatus.Released, isPlaying = false) }
    }
}
