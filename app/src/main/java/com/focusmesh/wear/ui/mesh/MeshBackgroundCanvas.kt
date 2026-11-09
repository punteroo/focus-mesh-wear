package com.focusmesh.wear.ui.mesh

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.ui.theme.PhasePalette
import com.focusmesh.wear.ui.theme.paletteForPhase
import kotlin.math.cos
import kotlin.math.sin

// Renders either the 3D perspective animated mesh or the 2D subtle mesh background.
// Automatically halts animation loops when the watch enters Ambient (Always-On Display) mode to save battery.
@Composable
fun MeshBackgroundCanvas(
    phase: TimerPhase,
    mesh3dEnabled: Boolean,
    isAmbient: Boolean,
    wavePhysicsState: WavePhysicsState,
    modifier: Modifier = Modifier
) {
    val targetPalette: PhasePalette = paletteForPhase(phase)

    val primaryColor by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 700),
        label = "meshPrimaryColor"
    )
    val secondaryColor by animateColorAsState(
        targetValue = targetPalette.secondary,
        animationSpec = tween(durationMillis = 700),
        label = "meshSecondaryColor"
    )
    val deepBgColor by animateColorAsState(
        targetValue = targetPalette.deepBackground,
        animationSpec = tween(durationMillis = 700),
        label = "meshDeepBgColor"
    )

    var animationTimeSeconds by remember { mutableFloatStateOf(0.0f) }
    val trianglePath = remember { Path() }

    val gridSize = wavePhysicsState.gridSize
    val projectedX = remember(gridSize) { FloatArray(gridSize * gridSize) }
    val projectedY = remember(gridSize) { FloatArray(gridSize * gridSize) }
    val vertexElevation = remember(gridSize) { FloatArray(gridSize * gridSize) }

    // Frame clock loop runs only when the watch is awake and not in Always-On Display (AOD) mode.
    LaunchedEffect(isAmbient) {
        if (isAmbient) {
            wavePhysicsState.clear()
            return@LaunchedEffect
        }
        var startNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (startNanos == 0L) startNanos = frameNanos
                animationTimeSeconds = ((frameNanos - startNanos) / 1_000_000_000.0).toFloat()
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

        // In Ambient mode, draw a pure black background to minimize OLED pixel power consumption.
        if (isAmbient) {
            drawRect(color = Color.Black)
        } else {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(deepBgColor, Color(0xFF050608)),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.15f
                )
            )
        }

        val t = if (isAmbient) 0.0f else animationTimeSeconds
        val tiltRadians = if (mesh3dEnabled) 0.58f else 0.0f
        val cosTilt = cos(tiltRadians)
        val sinTilt = sin(tiltRadians)
        val cameraDistance = 2.55f
        val scale = if (mesh3dEnabled) radius * 1.62f else radius * 1.05f

        // Compute 3D or 2D vertex positions across the lattice.
        for (row in 0 until gridSize) {
            val ny = -1.0f + (2.0f * row) / (gridSize - 1)
            for (col in 0 until gridSize) {
                val nx = -1.0f + (2.0f * col) / (gridSize - 1)
                val idx = row * gridSize + col
                val touchRipple = wavePhysicsState.displacementAt(row, col)

                if (mesh3dEnabled) {
                    val waveA = sin(nx * 3.1f + t * 1.65f) * cos(ny * 2.7f + t * 1.25f) * 0.22f
                    val waveB = sin((nx + ny) * 2.2f - t * 1.1f) * 0.12f
                    val z = if (isAmbient) 0.0f else (waveA + waveB + touchRipple * 0.42f)
                    vertexElevation[idx] = z

                    val rotatedY = ny * cosTilt - z * sinTilt
                    val rotatedZ = ny * sinTilt + z * cosTilt + cameraDistance
                    val perspective = 1.0f / rotatedZ.coerceAtLeast(0.5f)

                    projectedX[idx] = centerX + (nx * scale * perspective)
                    projectedY[idx] = centerY + (rotatedY * scale * perspective)
                } else {
                    // 2D mode uses very slight vertex drift and radial touch displacement without 3D tilt.
                    val driftX = if (isAmbient) 0.0f else sin(ny * 2.5f + t * 0.55f) * 0.025f
                    val driftY = if (isAmbient) 0.0f else cos(nx * 2.5f + t * 0.45f) * 0.025f
                    val rippleOffset = touchRipple * 0.08f
                    vertexElevation[idx] = touchRipple + (driftX + driftY) * 4.0f

                    projectedX[idx] = centerX + (nx + driftX + nx * rippleOffset) * scale
                    projectedY[idx] = centerY + (ny + driftY + ny * rippleOffset) * scale
                }
            }
        }

        // Draw shaded triangular mesh cells and wireframe edges.
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

                val avgElevation = (vertexElevation[i00] + vertexElevation[i10] + vertexElevation[i01] + vertexElevation[i11]) * 0.25f
                val elevationNorm = ((avgElevation + 0.35f) / 0.70f).coerceIn(0.0f, 1.0f)

                if (!isAmbient) {
                    val facetAlpha = if (mesh3dEnabled) {
                        (0.08f + elevationNorm * 0.22f).coerceIn(0.05f, 0.32f)
                    } else {
                        (0.05f + elevationNorm * 0.10f).coerceIn(0.04f, 0.16f)
                    }
                    val facetColor = lerpColor(secondaryColor, primaryColor, elevationNorm).copy(alpha = facetAlpha)

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
                    isAmbient -> 0.18f
                    mesh3dEnabled -> (0.24f + elevationNorm * 0.48f).coerceIn(0.20f, 0.75f)
                    else -> 0.26f
                }
                val lineColor = lerpColor(secondaryColor, primaryColor, elevationNorm).copy(alpha = lineAlpha)
                val strokeWidth = if (isAmbient) 1.0f else 1.45f

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

        // Draw a subtle radial vignette around the watch bezel so the central timer digits remain high-contrast.
        if (!isAmbient) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = radius * 0.96f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.0f)
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
