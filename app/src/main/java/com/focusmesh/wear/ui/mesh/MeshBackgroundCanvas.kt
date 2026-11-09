package com.focusmesh.wear.ui.mesh

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core. FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.ui.theme.PhasePalette
import com.focusmesh.wear.ui.theme.paletteForTimerState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Renders a full-bleed 3D perspective mesh or 2D subtle mesh that covers the entire circular watch screen.
// Shifts color from Paused Orange into the active phase color (Red, Light Blue, Yellow) when running.
// Halts animation completely in Ambient (Always-On Display) mode to save battery.
@Composable
fun MeshBackgroundCanvas(
    phase: TimerPhase,
    isRunning: Boolean,
    isAwaitingManualContinue: Boolean,
    progressFraction: Float,
    mesh3dEnabled: Boolean,
    isAmbient: Boolean,
    wavePhysicsState: WavePhysicsState,
    modifier: Modifier = Modifier
) {
    val targetPalette: PhasePalette = paletteForTimerState(
        phase = phase,
        isRunning = isRunning,
        isAwaitingManualContinue = isAwaitingManualContinue
    )

    val primaryColor by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "meshPrimaryColor"
    )
    val secondaryColor by animateColorAsState(
        targetValue = targetPalette.secondary,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "meshSecondaryColor"
    )
    val deepBgColor by animateColorAsState(
        targetValue = targetPalette.deepBackground,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "meshDeepBgColor"
    )

    // Smoothly transitions wave amplitude and flow speed between paused idle state and active running state.
    val activeEnergy by animateFloatAsState(
        targetValue = if (isRunning) 1.0f else 0.38f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "meshActiveEnergy"
    )

    var phaseTimeSeconds by remember { mutableFloatStateOf(0.0f) }
    val trianglePath = remember { Path() }

    val gridSize = wavePhysicsState.gridSize
    val projectedX = remember(gridSize) { FloatArray(gridSize * gridSize) }
    val projectedY = remember(gridSize) { FloatArray(gridSize * gridSize) }
    val vertexElevation = remember(gridSize) { FloatArray(gridSize * gridSize) }

    // Frame clock loop advances phase time proportional to activeEnergy so waves accelerate smoothly when started.
    LaunchedEffect(isAmbient) {
        if (isAmbient) {
            wavePhysicsState.clear()
            return@LaunchedEffect
        }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0.0f, 0.1f)
                    val speedMultiplier = if (isRunning) 1.35f else 0.42f
                    phaseTimeSeconds += dt * speedMultiplier
                }
                lastNanos = frameNanos
                wavePhysicsState.stepSimulation()
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val radius = width.coerceAtMost(height) * 0.5f

        if (isAmbient) {
            drawRect(color = Color.Black)
        } else {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(deepBgColor, Color(0xFF040507)),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.25f
                )
            )
        }

        val t = if (isAmbient) 0.0f else phaseTimeSeconds
        // Tilt angle and frustum scale chosen so the lattice overscans 100% of the circular watch screen.
        val tiltRadians = if (mesh3dEnabled) 0.46f else 0.0f
        val cosTilt = cos(tiltRadians)
        val sinTilt = sin(tiltRadians)
        val cameraDistance = 2.15f
        // Wide world span ensures the mesh edges remain outside the circular bezel even at the top horizon.
        val worldSpan = if (mesh3dEnabled) 2.25f else 1.35f
        val focalScale = if (mesh3dEnabled) radius * 1.75f else radius * 1.05f

        for (row in 0 until gridSize) {
            val normRow = -1.0f + (2.0f * row) / (gridSize - 1)
            val wy = normRow * worldSpan
            for (col in 0 until gridSize) {
                val normCol = -1.0f + (2.0f * col) / (gridSize - 1)
                val wx = normCol * worldSpan
                val idx = row * gridSize + col
                val touchRipple = wavePhysicsState.displacementAt(row, col)
                val radialDist = sqrt(normCol * normCol + normRow * normRow)

                if (mesh3dEnabled) {
                    val primaryWave = sin(wx * 1.85f + t * 1.6f) * cos(wy * 1.65f - t * 1.2f) * (0.28f * activeEnergy)
                    val radialWave = cos(radialDist * 3.8f - t * 2.1f) * (0.16f * activeEnergy)
                    val z = if (isAmbient) 0.0f else (primaryWave + radialWave + touchRipple * 0.52f)
                    vertexElevation[idx] = z

                    val rotatedY = wy * cosTilt - z * sinTilt
                    val rotatedZ = (wy * sinTilt + z * cosTilt + cameraDistance).coerceAtLeast(0.65f)
                    val invZ = 1.0f / rotatedZ

                    projectedX[idx] = centerX + (wx * focalScale * invZ)
                    projectedY[idx] = centerY + (rotatedY * focalScale * invZ)
                } else {
                    // 2D mode spans edge-to-edge with subtle vertex breathing and touch wave displacement.
                    val driftX = if (isAmbient) 0.0f else sin(wy * 2.2f + t * 0.9f) * (0.045f * activeEnergy)
                    val driftY = if (isAmbient) 0.0f else cos(wx * 2.2f - t * 0.8f) * (0.045f * activeEnergy)
                    val rippleScale = touchRipple * 0.12f
                    vertexElevation[idx] = touchRipple + (driftX + driftY) * 3.5f

                    projectedX[idx] = centerX + (wx + driftX + normCol * rippleScale) * focalScale
                    projectedY[idx] = centerY + (wy + driftY + normRow * rippleScale) * focalScale
                }
            }
        }

        // Render full-screen triangulated facets and glowing wireframe lines across the entire viewport.
        for (row in 0 until gridSize - 1) {
            for (col in 0 until gridSize - 1) {
                val i00 = row * gridSize + col
                val i10 = row * gridSize + (col + 1)
                val i01 = (row + 1) * gridSize + col
                val i11 = (row + 1) * gridSize + (col + 1)

                val x00 = projectedX[i00]
                val y00 = projectedY[i00]
                val x10 = projectedX[i10]
                val y10 = projectedY[i10]
                val x01 = projectedX[i01]
                val y01 = projectedY[i01]
                val x11 = projectedX[i11]
                val y11 = projectedY[i11]

                val avgZ = (vertexElevation[i00] + vertexElevation[i10] + vertexElevation[i01] + vertexElevation[i11]) * 0.25f
                val elevationNorm = ((avgZ + 0.40f) / 0.80f).coerceIn(0.0f, 1.0f)

                if (!isAmbient) {
                    val baseFacetAlpha = if (mesh3dEnabled) {
                        0.10f + elevationNorm * (0.16f + 0.14f * activeEnergy)
                    } else {
                        0.07f + elevationNorm * 0.12f
                    }
                    val facetColor = lerpColor(secondaryColor, primaryColor, elevationNorm)
                        .copy(alpha = baseFacetAlpha.coerceIn(0.06f, 0.40f))

                    trianglePath.reset()
                    trianglePath.moveTo(x00, y00)
                    trianglePath.lineTo(x10, y10)
                    trianglePath.lineTo(x01, y01)
                    trianglePath.close()
                    drawPath(path = trianglePath, color = facetColor)

                    trianglePath.reset()
                    trianglePath.moveTo(x10, y10)
                    trianglePath.lineTo(x11, y11)
                    trianglePath.lineTo(x01, y01)
                    trianglePath.close()
                    drawPath(path = trianglePath, color = facetColor)
                }

                val lineAlpha = when {
                    isAmbient -> 0.16f
                    mesh3dEnabled -> (0.28f + elevationNorm * (0.38f + 0.22f * activeEnergy)).coerceIn(0.24f, 0.88f)
                    else -> (0.24f + 0.14f * activeEnergy).coerceIn(0.22f, 0.45f)
                }
                val lineColor = lerpColor(secondaryColor, primaryColor, elevationNorm).copy(alpha = lineAlpha)
                val strokeWidth = if (isAmbient) 1.0f else (1.3f + 0.4f * activeEnergy)

                drawLine(color = lineColor, start = Offset(x00, y00), end = Offset(x10, y10), strokeWidth = strokeWidth)
                drawLine(color = lineColor, start = Offset(x00, y00), end = Offset(x01, y01), strokeWidth = strokeWidth)
                if (mesh3dEnabled && !isAmbient) {
                    drawLine(
                        color = lineColor.copy(alpha = lineAlpha * 0.55f),
                        start = Offset(x10, y10),
                        end = Offset(x01, y01),
                        strokeWidth = 0.9f
                    )
                }
            }
        }

        // Draw light inner depth shading and an animated progress / status ring around the watch perimeter.
        if (!isAmbient) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.15f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.38f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )

            // Subtle perimeter track and animated progress arc indicating active vs paused timer status.
            val ringRadius = radius * 0.94f
            val ringTopLeft = Offset(centerX - ringRadius, centerY - ringRadius)
            val ringSize = androidx.compose.ui.geometry.Size(ringRadius * 2.0f, ringRadius * 2.0f)

            drawArc(
                color = primaryColor.copy(alpha = 0.20f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = 3.0f)
            )

            val sweep = (progressFraction.coerceIn(0.0f, 1.0f) * 360.0f)
            val pulseAlpha = if (isRunning) {
                0.70f + 0.25f * sin(t * 2.6f)
            } else {
                0.45f + 0.20f * sin(t * 1.8f)
            }
            drawArc(
                color = primaryColor.copy(alpha = pulseAlpha.coerceIn(0.25f, 0.95f)),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = if (isRunning) 4.5f else 3.2f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0.0f, 1.0f)
    return Color(
        red = start.red + (stop.red - start.red) * t,
        green = start.green + (stop.green - start.green) * t,
        blue = start.blue + (stop.blue - start.blue) * t,
        alpha = start.alpha + (stop.alpha - start.alpha) * t
    )
}
