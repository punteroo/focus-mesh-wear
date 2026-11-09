package com.focusmesh.wear.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.TimerState

// Renders a transparent-background Bitmap Icon containing the live MM:SS timer text and a phase progress ring.
// Wear OS OngoingActivity displays the static/animated Icon at the bottom of the watch face, while Status text
// is only shown in the app launcher Recents list. Rendering the countdown directly into the Icon ensures the
// watch face pill displays the live timer readout in both full-color and monochrome-tinted watch face modes.
object OngoingPillIconRenderer {

    private const val ICON_SIZE = 96
    private val arcBounds = RectF(6f, 6f, 90f, 90f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
        color = Color.argb(75, 255, 255, 255)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 27.5f
        letterSpacing = -0.04f
    }

    // Creates an android.graphics.drawable.Icon displaying the current remaining time and phase progress arc.
    fun createTimerIcon(state: TimerState, readinessLabel: String): Icon {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val ringColor = resolveRingColor(state)
        progressPaint.color = ringColor

        // Draw subtle background ring and active countdown progress arc.
        canvas.drawOval(arcBounds, trackPaint)
        val sweepAngle = (state.progressFraction().coerceIn(0.0f, 1.0f) * 360.0f)
        canvas.drawArc(arcBounds, -90.0f, sweepAngle, false, progressPaint)

        // Draw high-contrast countdown text centered inside the badge.
        val label = if (state.phase == TimerPhase.READINESS && state.totalPhaseMillis <= 0L) {
            readinessLabel.take(5)
        } else {
            state.formattedTime()
        }

        textPaint.textSize = if (label.length <= 4) 30.0f else 27.0f
        val fontMetrics = textPaint.fontMetrics
        val baselineY = (ICON_SIZE * 0.5f) - (fontMetrics.ascent + fontMetrics.descent) * 0.5f
        canvas.drawText(label, ICON_SIZE * 0.5f, baselineY, textPaint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun resolveRingColor(state: TimerState): Int {
        if (!state.isRunning && !state.isAwaitingManualContinue) {
            // Paused Orange.
            return Color.rgb(255, 109, 26)
        }
        return when (state.phase) {
            TimerPhase.WORK -> Color.rgb(255, 77, 90)
            TimerPhase.BREAK -> Color.rgb(56, 214, 255)
            TimerPhase.READINESS -> Color.rgb(255, 221, 38)
        }
    }
}
