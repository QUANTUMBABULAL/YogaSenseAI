package com.chinmai.yogasense.ui.learning.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.ContentFrame
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.TagStyle
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Radius
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.SurfaceMuted
import com.chinmai.yogasense.ui.theme.WarmWhite
import kotlinx.coroutines.delay

/**
 * Single reusable demonstration player for every asana and media type.
 *
 * - [media] decides the renderer (video / animated illustration / static illustration).
 * - Auto-plays when [autoPlay] and the media is playable; pauses on ON_PAUSE and resumes on ON_RESUME.
 * - Releases the underlying player when leaving composition or when [media] changes.
 * - If a video fails, switches to [fallback] and says so.
 */
@Composable
fun AsanaAnimationPlayer(
    media: LessonMedia,
    modifier: Modifier = Modifier,
    fallback: LessonMedia? = null,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    caption: String? = null,
) {
    var videoFailed by remember(media) { mutableStateOf(false) }
    val active = if (videoFailed && fallback != null) fallback else media

    val context = LocalContext.current
    val playback: DemoPlayback = remember(active) {
        when (active) {
            is LessonMedia.Video -> VideoPlayback(context, active.assetPath, autoPlay, loop)
            is LessonMedia.AnimatedIllustration -> FigurePlayback(FigureTimeline(active.keyframes), autoPlay, loop)
            is LessonMedia.StaticIllustration -> NoPlayback
        }
    }
    DisposableEffect(playback) { onDispose { playback.release() } }

    // Lifecycle: pause in background, resume only if it was playing.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, playback) {
        var resumeOnReturn = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { resumeOnReturn = playback.state.value.isPlaying; playback.pause() }
                Lifecycle.Event.ON_RESUME -> if (resumeOnReturn) playback.play()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val state by playback.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) { if (state.status == PlaybackStatus.Error && active is LessonMedia.Video) videoFailed = true }
    // Drive time: frame clock for the illustration, 250 ms polling for video position.
    LaunchedEffect(playback, state.isPlaying) {
        when (playback) {
            is FigurePlayback -> if (state.isPlaying) while (true) withFrameMillis { playback.tick(it) }
            is VideoPlayback -> while (true) { playback.sync(); delay(if (state.isPlaying) 250 else 1000) }
            else -> Unit
        }
    }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val playable = active !is LessonMedia.StaticIllustration

    Column(modifier.clip(RoundedCornerShape(Radius.hero)).background(CardWhite)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.1f).background(LimeTint)) {
            if (fullscreen) {
                Text("Playing in full screen", style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.align(Alignment.Center))
            } else {
                Stage(active, playback, state, Modifier.fillMaxSize())
            }
            MediaLabel(active, videoFailed, Modifier.align(Alignment.TopStart).padding(Spacing.sm + 4.dp))
        }
        if (playable) {
            Controls(state, playback, onFullscreen = { fullscreen = true })
        } else {
            Text(
                "Demonstration video not available yet.",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
                modifier = Modifier.padding(Spacing.md),
            )
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md - 4.dp))
        }
    }

    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Midnight).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MediaLabel(active, videoFailed, Modifier.padding(start = Spacing.sm))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { fullscreen = false }, modifier = Modifier.size(48.dp)) {
                        Icon(YsIcons.Close, "Exit full screen", tint = WarmWhite)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(Spacing.md).clip(RoundedCornerShape(Radius.hero)).background(LimeTint)) {
                    Stage(active, playback, state, Modifier.fillMaxSize())
                }
                Controls(state, playback, onFullscreen = null, dark = true)
            }
        }
    }
}

@Composable
private fun Stage(media: LessonMedia, playback: DemoPlayback, state: PlaybackState, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (media) {
            is LessonMedia.Video -> {
                val vp = playback as? VideoPlayback
                if (vp != null && state.status != PlaybackStatus.Released) {
                    ContentFrame(player = vp.player, modifier = Modifier.fillMaxSize())
                }
                if (state.status == PlaybackStatus.Loading) {
                    CircularProgressIndicator(color = Coral, trackColor = SurfaceMuted, modifier = Modifier.size(36.dp))
                }
            }
            is LessonMedia.AnimatedIllustration -> {
                val fp = playback as FigurePlayback
                AsanaFigure(fp.timeline.frameAt(state.positionMs), Modifier.fillMaxSize().padding(Spacing.lg))
            }
            is LessonMedia.StaticIllustration -> AsanaFigure(media.figure, Modifier.fillMaxSize().padding(Spacing.lg))
        }
    }
}

@Composable
private fun MediaLabel(media: LessonMedia, videoFailed: Boolean, modifier: Modifier) {
    val text = when (media) {
        is LessonMedia.Video -> "Video demonstration"
        is LessonMedia.AnimatedIllustration -> if (videoFailed) "Video unavailable · animated illustration" else "Animated illustration · not a video"
        is LessonMedia.StaticIllustration -> "Static illustration"
    }
    Tag(text, modifier, style = TagStyle.Outline)
}

@Composable
private fun Controls(state: PlaybackState, playback: DemoPlayback, onFullscreen: (() -> Unit)?, dark: Boolean = false) {
    val fg = if (dark) WarmWhite else Ink
    val muted = if (dark) WarmWhite.copy(alpha = 0.7f) else InkMuted
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = playback::togglePlayPause,
            enabled = state.status != PlaybackStatus.Error,
            modifier = Modifier.size(48.dp).semantics { stateDescription = if (state.isPlaying) "Playing" else "Paused" },
            colors = IconButtonDefaults.iconButtonColors(containerColor = if (dark) Coral else Midnight, contentColor = if (dark) Midnight else WarmWhite),
        ) { Icon(if (state.isPlaying) YsIcons.Pause else YsIcons.Play, if (state.isPlaying) "Pause" else "Play", modifier = Modifier.size(20.dp)) }
        IconButton(onClick = playback::replay, modifier = Modifier.size(48.dp)) { Icon(YsIcons.Replay, "Replay", tint = fg) }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.xs)) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).semantics { contentDescription = "Demonstration progress" },
                color = Coral,
                trackColor = if (dark) Color(0x33FFFFFF) else SurfaceMuted,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.status == PlaybackStatus.Error) "Playback error" else "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                style = MaterialTheme.typography.labelMedium, color = muted, textAlign = TextAlign.Start,
            )
        }
        if (onFullscreen != null) {
            IconButton(onClick = onFullscreen, modifier = Modifier.size(48.dp)) { Icon(YsIcons.Fullscreen, "Full screen", tint = fg) }
        } else {
            Spacer(Modifier.width(Spacing.sm))
        }
    }
}
