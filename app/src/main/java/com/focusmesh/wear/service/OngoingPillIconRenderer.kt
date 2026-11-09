package com.focusmesh.wear.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.TimerState

// Renders a full-bleed, high-contrast 2-row stacked countdown Bitmap Icon for the watch face pill.
// Because the watch face scales the OngoingActivity icon into a compact ~24dp badge, stacking the
// primary and secondary time units vertically (e.g. "59" over ":42", or "1h" over "00m") allows the
// digits to be nearly twice as tall as a single horizontal line and fill the entire 96x96 canvas.
object OngoingPillIconRenderer {

    private const val ICON_SIZE = 96
    private const val MAX_ROW_WIDTH = 78.0f

    private val condensedBoldTypeface: Typeface = Typeface.create(
        "sans-serif-condensed",
        Typeface.BOLD
    )

    private val topRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = condensedBoldTypeface
        isSubpixelText = true
    }

    private val bottomRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = condensedBoldTypeface
        isSubpixelText = true
    }

    private val textBounds = Rect()

    // Renders a 96x96 transparent-background Bitmap with oversized stacked countdown digits.
    fun renderTimerBitmap(state: TimerState, readinessLabel: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = ICON_SIZE * 0.5f
        val accentColor = resolveAccentColor(state)

        if (state.phase == TimerPhase.READINESS && state.totalPhaseMillis <= 0L) {
            val label = readinessLabel.take(3).uppercase()
            topRowPaint.color = accentColor
            applyFittedTextSize(topRowPaint, label, preferredSize = 48.0f, maxWidth = 84.0f)
            topRowPaint.getTextBounds(label, 0, label.length, textBounds)
            val centeredY = (ICON_SIZE * 0.5f) - textBounds.exactCenterY()
            canvas.drawText(label, centerX, centeredY, topRowPaint)
            return bitmap
        }

        val totalSeconds = (state.remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        val topText: String
        val bottomText: String
        if (hours > 0L) {
            topText = String.format("%dh", hours)
            bottomText = String.format("%02dm", minutes)
        } else {
            topText = String.format("%02d", minutes)
            bottomText = String.format(":%02d", seconds)
        }

        topRowPaint.color = Color.WHITE
        applyFittedTextSize(topRowPaint, topText, preferredSize = 52.0f, maxWidth = MAX_ROW_WIDTH)
        topRowPaint.getTextBounds(topText, 0, topText.length, textBounds)
        val topBaselineY = 27.0f - textBounds.exactCenterY()
        canvas.drawText(topText, centerX, topBaselineY, topRowPaint)

        bottomRowPaint.color = accentColor
        applyFittedTextSize(bottomRowPaint, bottomText, preferredSize = 46.0f, maxWidth = MAX_ROW_WIDTH)
        bottomRowPaint.getTextBounds(bottomText, 0, bottomText.length, textBounds)
        val bottomBaselineY = 70.0f - textBounds.exactCenterY()
        canvas.drawText(bottomText, centerX, bottomBaselineY, bottomRowPaint)

        return bitmap
    }

    // Creates an android.graphics.drawable.Icon displaying the current remaining time.
    fun createTimerIcon(state: TimerState, readinessLabel: String): Icon {
        return Icon.createWithBitmap(renderTimerBitmap(state, readinessLabel))
    }

    private fun applyFittedTextSize(
        paint: Paint,
        text: String,
        preferredSize: Float,
        maxWidth: Float
    ) {
        paint.textSize = preferredSize
        val measuredWidth = paint.measureText(text)
        if (measuredWidth > maxWidth && measuredWidth > 0.0f) {
            paint.textSize = preferredSize * (maxWidth / measuredWidth)
        }
    }

    private fun resolveAccentColor(state: TimerState): Int {
        if (!state.isRunning && !state.isAwaitingManualContinue) {
            // High-luminance Paused Orange for legibility on dark watch face pills.
            return Color.rgb(255, 138, 51)
        }
        return when (state.phase) {
            TimerPhase.WORK -> Color.rgb(255, 102, 115)
            TimerPhase.BREAK -> Color.rgb(82, 224, 255)
            TimerPhase.READINESS -> Color.rgb(255, 228, 77)
        }
    }
}

