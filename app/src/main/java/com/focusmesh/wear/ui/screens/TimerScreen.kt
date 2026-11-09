package com.focusmesh.wear.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.focusmesh.wear.ui.theme.paletteForTimerState
import com.focusmesh.wear.ui.theme.resolveGoogleFontFamily

// Renders the uncluttered main timer screen over the full-screen interactive 3D or 2D mesh background.
// Shifts color from Paused Orange into the active phase color when started, accompanied by a radial wave
// and subtle scale and breathing animations.
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
    val wavePhysicsState = remember { WavePhysicsState(gridSize = 17) }
    val fontFamily = remember(preferences.fontChoice) {
        resolveGoogleFontFamily(preferences.fontChoice)
    }
    val targetPalette = paletteForTimerState(
        phase = timerState.phase,
        isRunning = timerState.isRunning,
        isAwaitingManualContinue = timerState.isAwaitingManualContinue
    )

    val accentColor by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "timerAccentColor"
    )

    // Trigger a concentric shockwave across the full-screen mesh whenever the timer starts, pauses, or changes phase.
    var hasInitializedWave by remember { mutableStateOf(false) }
    LaunchedEffect(timerState.isRunning, timerState.phase) {
        if (hasInitializedWave && !isAmbient) {
            val pulseIntensity = if (timerState.isRunning) 1.05f else 0.65f
            wavePhysicsState.triggerStateTransitionWave(intensity = pulseIntensity)
        }
        hasInitializedWave = true
    }

    // Subtle spring scale transition when toggling between paused (0.93f) and active running (1.0f).
    val timerScale by animateFloatAsState(
        targetValue = if (timerState.isRunning) 1.0f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "timerDigitsScale"
    )

    // Subtle continuous breathing animation for the status indicator dot and paused state hint.
    val infiniteTransition = rememberInfiniteTransition(label = "timerBreathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

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
                        // Reserve the rightmost 15% of the screen width for pulling in the settings menu from the right side.
                        if (pos.x < size.width * 0.85f && size.width > 0 && size.height > 0) {
                            val normX = ((pos.x / size.width) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                            val normY = ((pos.y / size.height) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                            wavePhysicsState.applyTouchImpulse(normX, normY, strength = 0.58f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        MeshBackgroundCanvas(
            phase = timerState.phase,
            isRunning = timerState.isRunning,
            isAwaitingManualContinue = timerState.isAwaitingManualContinue,
            progressFraction = timerState.progressFraction(),
            mesh3dEnabled = preferences.mesh3dEnabled,
            isAmbient = isAmbient,
            wavePhysicsState = wavePhysicsState
        )

        // Only tapping or long-pressing directly on the central time element toggles or skips the timer.
        // Touching anywhere else on the screen interacts purely with the 3D mesh waves.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(if (isAmbient) 1.0f else timerScale)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onPrimaryTap,
                    onLongClick = onLongPressSkip
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Minimal phase header with a subtle animated status dot.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (!isAmbient) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = accentColor.copy(
                                    alpha = if (timerState.isRunning) breathingAlpha else 0.85f
                                ),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = phaseLabel,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 2.4.sp,
                        color = if (isAmbient) {
                            accentColor.copy(alpha = 0.65f)
                        } else {
                            accentColor.copy(alpha = 0.95f)
                        }
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Primary uncluttered countdown display in the center of the watch face.
            Text(
                text = centerDisplayText,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (centerDisplayText.length > 5) 38.sp else 48.sp,
                    letterSpacing = 1.0.sp,
                    color = when {
                        isAmbient -> Color.White.copy(alpha = 0.82f)
                        timerState.isRunning -> Color.White
                        else -> Color.White.copy(alpha = 0.78f + 0.18f * breathingAlpha)
                    }
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
                        letterSpacing = 1.8.sp,
                        color = accentColor.copy(alpha = 0.65f + 0.30f * breathingAlpha)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
