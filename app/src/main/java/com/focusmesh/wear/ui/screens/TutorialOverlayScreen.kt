package com.focusmesh.wear.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.focusmesh.wear.R

// Interactive 4-step onboarding and reference tutorial re-openable from Settings at any time.
// Renders animated vector diagrams with a vector pointing hand icon demonstrating:
// 1) Swiping left from the right edge to open configurations
// 2) Tapping the central clock to start/pause (and preset label above it)
// 3) Official Wear OS watch face complications
// 4) Swiping vertically (up or down) to switch between Standard and Flexible screens, and long-tapping the clock to swap Work and Break
@Composable
fun TutorialOverlayScreen(
    onFinishTutorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    BackHandler {
        if (currentStep > 0) {
            currentStep -= 1
        } else {
            onFinishTutorial()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tutorialVectorAnim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vectorGestureProgress"
    )

    val stepTitle = when (currentStep) {
        0 -> stringResource(R.string.tutorial_step_1_title)
        1 -> stringResource(R.string.tutorial_step_2_title)
        2 -> stringResource(R.string.tutorial_step_3_title)
        else -> stringResource(R.string.tutorial_step_4_title)
    }

    val stepBody = when (currentStep) {
        0 -> stringResource(R.string.tutorial_step_1_body)
        1 -> stringResource(R.string.tutorial_step_2_body)
        2 -> stringResource(R.string.tutorial_step_3_body)
        else -> stringResource(R.string.tutorial_step_4_body)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
            .pointerInput(currentStep) {
                detectDragGestures { _, dragAmount ->
                    if (currentStep == 0 && dragAmount.x < -18f) {
                        currentStep = 1
                    } else if (currentStep == 3 && kotlin.math.abs(dragAmount.y) > 18f) {
                        onFinishTutorial()
                    }
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Step progress dots at top of circular watch screen.
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            for (index in 0 until totalSteps) {
                val isCurrent = index == currentStep
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isCurrent) 7.dp else 5.dp)
                        .background(
                            color = if (isCurrent) Color(0xFFFF6D1A) else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }

        // Animated vector demonstration canvas with hand icon and motion arrows.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(66.dp)
                .clickable {
                    if (currentStep < totalSteps - 1) {
                        currentStep += 1
                    } else {
                        onFinishTutorial()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (currentStep) {
                    0 -> drawRightEdgeSwipeDemo(animProgress)
                    1 -> drawTapClockDemo(animProgress)
                    2 -> drawComplicationDemo(animProgress)
                    else -> drawBottomSwipeFlexibleDemo(animProgress)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stepTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6D1A),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stepBody,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Native Wear OS circular Button navigation controls.
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Button(
                onClick = {
                    if (currentStep > 0) {
                        currentStep -= 1
                    } else {
                        onFinishTutorial()
                    }
                },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(ButtonDefaults.ExtraSmallButtonSize)
            ) {
                Icon(
                    imageVector = if (currentStep > 0) {
                        Icons.AutoMirrored.Filled.ArrowBack
                    } else {
                        Icons.Default.Close
                    },
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    if (currentStep < totalSteps - 1) {
                        currentStep += 1
                    } else {
                        onFinishTutorial()
                    }
                },
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = Color(0xFFFF6D1A),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(ButtonDefaults.ExtraSmallButtonSize)
            ) {
                Icon(
                    imageVector = if (currentStep < totalSteps - 1) {
                        Icons.AutoMirrored.Filled.ArrowForward
                    } else {
                        Icons.Default.Check
                    },
                    contentDescription = null
                )
            }
        }
    }
}

// Step 1 vector illustration: Watch bezel with highlighted right edge, leftward arrow, and animated hand icon.
private fun DrawScope.drawRightEdgeSwipeDemo(progress: Float) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.44f

    // Watch bezel outline.
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )

    // Glowing right-edge swipe zone arc.
    drawArc(
        color = Color(0xFFFF6D1A),
        startAngle = -42f,
        sweepAngle = 84f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )

    val startX = center.x + radius * 0.82f
    val endX = center.x - radius * 0.25f
    val handX = startX + (endX - startX) * progress
    val handY = center.y

    // Leftward motion arrow trail.
    drawHorizontalArrow(
        fromX = startX,
        toX = handX,
        y = handY - 6.dp.toPx(),
        color = Color(0xFF38D6FF)
    )

    // Vector hand icon dragging left from the right edge.
    drawVectorHandIcon(
        tipPosition = Offset(handX, handY),
        scale = 0.95f,
        tint = Color.White
    )
}

// Step 2 vector illustration: Central clock box with concentric tap ripples and animated tapping hand icon.
private fun DrawScope.drawTapClockDemo(progress: Float) {
    val center = Offset(size.width * 0.5f, size.height * 0.46f)

    // Preset pill above the clock.
    drawRoundRect(
        color = Color(0xFF38D6FF).copy(alpha = 0.35f),
        topLeft = Offset(center.x - 16.dp.toPx(), center.y - 20.dp.toPx()),
        size = Size(32.dp.toPx(), 7.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )

    // Central clock pill.
    drawRoundRect(
        color = Color(0xFFFF4D5A).copy(alpha = 0.28f),
        topLeft = Offset(center.x - 24.dp.toPx(), center.y - 9.dp.toPx()),
        size = Size(48.dp.toPx(), 18.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 1.8.dp.toPx())
    )

    // Concentric tap ripple ring expanding from the clock center.
    val rippleRadius = (6.dp.toPx() + 20.dp.toPx() * progress)
    drawCircle(
        color = Color(0xFFFF6D1A).copy(alpha = (1.0f - progress).coerceIn(0f, 1f)),
        radius = rippleRadius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )

    val tapBounce = kotlin.math.sin(progress * Math.PI.toFloat()) * 4.dp.toPx()
    drawVectorHandIcon(
        tipPosition = Offset(center.x + 4.dp.toPx(), center.y + 8.dp.toPx() - tapBounce),
        scale = 0.95f,
        tint = Color.White
    )
}

// Step 3 vector illustration: Watch face with circular gauge complication slots and hand pointing at slot.
private fun DrawScope.drawComplicationDemo(progress: Float) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.44f

    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )

    // Left complication slot.
    val leftSlot = Offset(center.x - radius * 0.42f, center.y - radius * 0.28f)
    drawCircle(
        color = Color.White.copy(alpha = 0.25f),
        radius = 8.dp.toPx(),
        center = leftSlot,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Highlighted FocusMesh complication gauge slot on the right.
    val activeSlot = Offset(center.x + radius * 0.35f, center.y - radius * 0.22f)
    val slotRadius = 10.dp.toPx()
    drawCircle(
        color = Color(0xFF38D6FF).copy(alpha = 0.25f),
        radius = slotRadius,
        center = activeSlot
    )
    drawArc(
        color = Color(0xFFFF6D1A),
        startAngle = -90f,
        sweepAngle = 270f * (0.4f + 0.6f * progress),
        useCenter = false,
        topLeft = Offset(activeSlot.x - slotRadius, activeSlot.y - slotRadius),
        size = Size(slotRadius * 2f, slotRadius * 2f),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )

    drawVectorHandIcon(
        tipPosition = Offset(activeSlot.x, activeSlot.y + 4.dp.toPx()),
        scale = 0.9f,
        tint = Color.White
    )
}

// Step 4 vector illustration: Dual concentric bezel rings with vertical up/down swipe arrow for Flexible Mode.
private fun DrawScope.drawBottomSwipeFlexibleDemo(progress: Float) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.44f
    val innerRadius = radius * 0.82f

    // Outer active bezel ring (Warm Coral).
    drawCircle(
        color = Color(0xFFFF4D5A).copy(alpha = 0.55f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.2.dp.toPx())
    )

    // Inner standby pool concentric ring (Cool Cyan) distinctive to Flexible Mode.
    drawCircle(
        color = Color(0xFF38D6FF).copy(alpha = 0.65f),
        radius = innerRadius,
        center = center,
        style = Stroke(width = 1.8.dp.toPx())
    )

    val topY = center.y - radius * 0.65f
    val bottomY = center.y + radius * 0.65f
    val wave = kotlin.math.sin(progress * Math.PI.toFloat())
    val handY = topY + (bottomY - topY) * wave

    drawVerticalUpArrow(
        x = center.x - 8.dp.toPx(),
        fromY = bottomY,
        toY = topY,
        color = Color(0xFFFF6D1A)
    )

    drawVectorHandIcon(
        tipPosition = Offset(center.x + 2.dp.toPx(), handY),
        scale = 0.95f,
        tint = Color.White
    )
}

// Draws a stylized vector pointing hand icon with index finger at tipPosition.
private fun DrawScope.drawVectorHandIcon(
    tipPosition: Offset,
    scale: Float,
    tint: Color
) {
    val u = 2.2.dp.toPx() * scale
    val path = Path().apply {
        // Index fingertip at tipPosition.
        moveTo(tipPosition.x, tipPosition.y)
        lineTo(tipPosition.x + 1.2f * u, tipPosition.y + 1.2f * u)
        lineTo(tipPosition.x + 1.2f * u, tipPosition.y + 4.2f * u)
        // Knuckles of middle/ring/pinky fingers.
        lineTo(tipPosition.x + 4.6f * u, tipPosition.y + 4.8f * u)
        lineTo(tipPosition.x + 4.8f * u, tipPosition.y + 9.2f * u)
        // Wrist base.
        lineTo(tipPosition.x - 1.2f * u, tipPosition.y + 9.2f * u)
        // Thumb extending left.
        lineTo(tipPosition.x - 3.4f * u, tipPosition.y + 6.2f * u)
        lineTo(tipPosition.x - 2.2f * u, tipPosition.y + 4.8f * u)
        lineTo(tipPosition.x - 0.8f * u, tipPosition.y + 5.6f * u)
        // Left side of index finger back to tip.
        lineTo(tipPosition.x - 0.8f * u, tipPosition.y + 1.2f * u)
        close()
    }

    drawPath(
        path = path,
        color = Color(0xFF0A0D14).copy(alpha = 0.85f)
    )
    drawPath(
        path = path,
        color = tint,
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawHorizontalArrow(
    fromX: Float,
    toX: Float,
    y: Float,
    color: Color
) {
    val head = 5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(fromX, y),
        end = Offset(toX, y),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(toX, y),
        end = Offset(toX + head, y - head * 0.7f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(toX, y),
        end = Offset(toX + head, y + head * 0.7f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawVerticalUpArrow(
    x: Float,
    fromY: Float,
    toY: Float,
    color: Color
) {
    val head = 5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(x, fromY),
        end = Offset(x, toY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(x, toY),
        end = Offset(x - head * 0.7f, toY + head),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(x, toY),
        end = Offset(x + head * 0.7f, toY + head),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}
