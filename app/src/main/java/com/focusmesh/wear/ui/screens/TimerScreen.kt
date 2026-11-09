package com.focusmesh.wear.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.TimerState
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.ui.mesh.MeshBackgroundCanvas
import com.focusmesh.wear.ui.mesh.WavePhysicsState
import com.focusmesh.wear.ui.theme.paletteForPhase
import com.focusmesh.wear.ui.theme.resolveGoogleFontFamily

// Renders the uncluttered main timer screen over the interactive 3D or 2D mesh background.
// Touch events outside the left-edge drawer gesture zone inject wave impulses into the mesh lattice.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerScreen(
    timerState: TimerState,
    preferences: UserPreferences,
    isAmbient: Boolean,
    onPrimaryTap: () -> Unit,
    onLongPressSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wavePhysicsState = remember { WavePhysicsState(gridSize = 13) }
    val fontFamily = remember(preferences.fontChoice) {
        resolveGoogleFontFamily(preferences.fontChoice)
    }
    val palette = paletteForPhase(timerState.phase)
    val interactionSource = remember { MutableInteractionSource() }

    val phaseLabel = when (timerState.phase) {
        TimerPhase.WORK -> stringResource(R.string.phase_work)
        TimerPhase.BREAK -> stringResource(R.string.phase_break)
        TimerPhase.READINESS -> stringResource(R.string.phase_readiness)
    }

    val centerDisplayText = if (
        timerState.phase == TimerPhase.READINESS &&
        !preferences.readinessGraceEnabled
    ) {
        stringResource(R.string.phase_readiness)
    } else {
        timerState.formattedTime()
    }

    val subHintText = when {
        isAmbient -> null
        timerState.phase == TimerPhase.READINESS && timerState.isAwaitingManualContinue ->
            stringResource(R.string.hint_tap_to_start)
        !timerState.isRunning -> stringResource(R.string.hint_paused)
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(preferences.touchWaveEnabled, isAmbient) {
                if (!preferences.touchWaveEnabled || isAmbient) return@pointerInput
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val pos = change.position
                        // Reserve the leftmost 15% of the screen width for the left-swipe settings gesture.
                        if (pos.x > size.width * 0.15f && size.width > 0 && size.height > 0) {
                            val normX = ((pos.x / size.width) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                            val normY = ((pos.y / size.height) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                            wavePhysicsState.applyTouchImpulse(normX, normY, strength = 0.55f)
                        }
                    }
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPrimaryTap,
                onLongClick = onLongPressSkip
            ),
        contentAlignment = Alignment.Center
    ) {
        MeshBackgroundCanvas(
            phase = timerState.phase,
            mesh3dEnabled = preferences.mesh3dEnabled,
            isAmbient = isAmbient,
            wavePhysicsState = wavePhysicsState
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            // Minimal phase indicator pill above the timer.
            Text(
                text = phaseLabel,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 2.4.sp,
                    color = if (isAmbient) {
                        palette.primary.copy(alpha = 0.65f)
                    } else {
                        palette.primary.copy(alpha = 0.90f)
                    }
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Primary uncluttered countdown display in the center of the watch face.
            Text(
                text = centerDisplayText,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (centerDisplayText.length > 5) 38.sp else 48.sp,
                    letterSpacing = 1.0.sp,
                    color = if (isAmbient) Color.White.copy(alpha = 0.85f) else Color.White
                ),
                textAlign = TextAlign.Center
            )

            if (subHintText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subHintText,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
