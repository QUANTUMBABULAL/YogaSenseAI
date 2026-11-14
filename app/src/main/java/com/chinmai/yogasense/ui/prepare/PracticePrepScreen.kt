package com.chinmai.yogasense.ui.prepare

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.catalog.CameraView
import com.chinmai.yogasense.ui.components.AsanaFigure
import com.chinmai.yogasense.ui.components.GlowCard
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.SelectChip
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite

/**
 * Practice preparation: choose the pose (or free practice), check the camera setup, and pick the
 * voice and visual assistance before the camera starts. Every option can still be changed from
 * the live screen's Voice and Visual sheets.
 *
 * @param initialAsanaId pre-selected pose (from Detail / Learning / Home); null = free practice.
 * @param onStart receives the chosen asana id (null = free practice).
 */
@Composable
fun PracticePrepScreen(
    initialAsanaId: String?,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onStart: (String?) -> Unit,
    vm: AssistanceViewModel = assistanceViewModel(),
) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf(AsanaCatalog.byId(initialAsanaId)?.takeIf { it.liveSupported }?.id) }
    val info = AsanaCatalog.byId(selectedId)
    val coach by vm.coach.collectAsStateWithLifecycle()
    val visual by vm.visual.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = CardWhite, contentColor = Ink),
            ) { Icon(YsIcons.Back, "Back") }
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text("Get ready", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text("Set up your space and guidance", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PoseChooser(selectedId) { selectedId = it }
            Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PoseHero(info, onOpenLesson)
                SetupChecklist(info)
                SectionHeader("Your assistance", Modifier.padding(top = Spacing.sm), subtitle = "Saved for next time · adjustable during practice")
                VoiceCoachCard(coach, vm::setCoach)
                VisualGuidanceCard(visual, hasTarget = info != null, onChange = vm::setVisual)
                Surface(color = CoralTint, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(Spacing.md)) {
                        Icon(YsIcons.Shield, null, tint = CoralDeep, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Practise within a comfortable range. Stop if you feel pain, dizziness or shortness of breath. " +
                                "Feedback is guidance from a single camera, not a precise body measurement.",
                            style = MaterialTheme.typography.bodySmall, color = Ink,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        Surface(color = CardWhite) {
            Column {
                HorizontalDivider(color = Hairline)
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.sm + 4.dp)) {
                    PrimaryButton(
                        if (info != null) "Start ${info.name}" else "Start free practice",
                        { onStart(selectedId) }, icon = YsIcons.Camera, modifier = Modifier.fillMaxWidth(),
                    )
                    if (!cameraGranted) {
                        Text(
                            "You'll be asked for camera access next. Video is analysed on this phone and never recorded.",
                            style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PoseChooser(selectedId: String?, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item { SelectChip("Any pose", selectedId == null, { onSelect(null) }) }
        items(AsanaCatalog.live, key = { it.id }) { a -> SelectChip(a.name, a.id == selectedId, { onSelect(a.id) }) }
    }
}

@Composable
private fun PoseHero(info: AsanaInfo?, onOpenLesson: (String) -> Unit) {
    GlowCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg - 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (info != null) "YOU'LL PRACTISE" else "FREE PRACTICE", style = MaterialTheme.typography.labelSmall, color = Lime)
                Spacer(Modifier.height(Spacing.xs))
                Text(info?.name ?: "Any supported pose", style = MaterialTheme.typography.headlineSmall, color = WarmWhite)
                Text(
                    info?.let { "${it.englishName} · hold ${it.holdLabel}" } ?: "Tadasana, Vrikshasana, Virabhadrasana II, Utkatasana or Trikonasana is recognised automatically",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
            if (info != null) {
                Spacer(Modifier.width(Spacing.sm))
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    AsanaFigure(info.figure, Modifier.size(96.dp), color = Lime)
                }
            }
        }
        if (info != null) {
            TextButton(onClick = { onOpenLesson(info.id) }, contentPadding = PaddingValues(0.dp)) {
                Icon(YsIcons.Play, null, tint = Lime, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Watch the lesson first", style = MaterialTheme.typography.labelLarge, color = WarmWhite)
            }
        }
    }
}

@Composable
private fun SetupChecklist(info: AsanaInfo?) {
    YsCard(Modifier.fillMaxWidth()) {
        Text("Camera setup", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(Spacing.xs))
        CheckRow(YsIcons.Camera, "Prop the phone upright at hip height, 2–3 m away")
        CheckRow(YsIcons.Profile, "Keep your whole body in frame, head to feet")
        CheckRow(
            YsIcons.Eye,
            when (info?.cameraView) {
                CameraView.SIDE -> "Stand side-on to the camera for this pose"
                CameraView.FRONT -> "Face the camera for this pose"
                null -> "Face the camera; stand side-on for Utkatasana"
            },
        )
        CheckRow(YsIcons.Leaf, "Clear space around you, on a non-slip surface")
    }
}
