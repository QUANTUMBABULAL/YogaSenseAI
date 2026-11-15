package com.chinmai.yogasense.ui.practice

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chinmai.yogasense.camera.CameraChoice
import com.chinmai.yogasense.camera.CameraLensPolicy
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.Accent
import com.chinmai.yogasense.ui.theme.Coral
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Surface1
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.Warn
import com.chinmai.yogasense.ui.theme.WarmWhite

/**
 * Which lenses the device declares, read from PackageManager so that choosing a camera never opens
 * one. CameraPreview re-checks with CameraX when it binds and falls back if this was wrong.
 */
fun declaredLenses(context: Context): Set<Int> {
    val pm = context.packageManager
    return buildSet {
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) add(CameraChoice.FRONT.lensFacing)
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) add(CameraChoice.BACK.lensFacing) // FEATURE_CAMERA = back-facing
    }
}

/**
 * Dedicated pre-session step of a guided session: an opaque screen (nothing runs underneath; the
 * camera is not bound yet) where the user picks the front (default) or back camera. Choosing only
 * changes the selection; [onStart] starts the session and the camera is bound when it's first needed.
 */
@Composable
fun GuidedCameraSetup(
    poseCount: Int,
    selected: Int,
    available: Set<Int>,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val resolution = CameraLensPolicy.resolve(selected, available)
    Box(
        Modifier.fillMaxSize().background(Midnight)
            // Nothing underneath may receive a touch.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (onClose != null) {
                IconButton(
                    onClick = onClose, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Surface1, contentColor = WarmWhite),
                ) { Icon(YsIcons.Close, "Close guided session") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "GUIDED SESSION", color = Midnight, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    modifier = Modifier.clip(CircleShape).background(Accent).padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Choose your camera", color = WarmWhite, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text("$poseCount poses. The camera turns on when it's time to do each pose, after its demonstration.", color = TextMuted, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CameraChoice.entries.forEach { c ->
                        val ok = c.lensFacing in available
                        CameraOption(
                            title = c.label + if (c == CameraChoice.FRONT) " (default)" else "",
                            detail = when {
                                !ok -> "Not available on this device"
                                c == CameraChoice.FRONT -> "Facing you — see yourself and the skeleton while you practise"
                                else -> "Facing away — for when the phone is placed or held by someone else"
                            },
                            selected = selected == c.lensFacing, enabled = ok, onClick = { onSelect(c.lensFacing) },
                        )
                    }
                }
                val message = when {
                    resolution == null -> "No camera was found on this device, so a guided session can't detect your poses."
                    else -> CameraLensPolicy.fallbackMessage(resolution)
                }
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = Warn, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Place the phone so your whole body will be visible.", color = TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onStart, enabled = resolution != null, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Midnight),
            ) { Text("Start session", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CameraOption(title: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent.copy(alpha = 0.16f) else Surface1)
            .border(2.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(20.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Midnight), contentAlignment = Alignment.Center) {
            Icon(YsIcons.Camera, null, tint = if (!enabled) TextMuted else Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) WarmWhite else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextMuted, fontSize = 13.sp)
        }
        // Radio indicator.
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Accent else TextMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(Accent)) }
    }
}
