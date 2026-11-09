package com.focusmesh.wear.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.TimerState
import com.focusmesh.wear.ui.MainActivity
import java.time.Instant

// Native Wear OS watch face complication provider supporting circular gauge slots (RANGED_VALUE),
// compact bezel or circle slots (SHORT_TEXT), and wide banner slots (LONG_TEXT).
// Uses CountDownTimeReference with TimeDifferenceStyle.STOPWATCH while running so the watch face
// natively renders a live-ticking countdown in its own complication typography.
class FocusTimerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val previewState = TimerState(
            phase = TimerPhase.WORK,
            isRunning = false,
            remainingMillis = 59L * 60_000L + 42_000L,
            totalPhaseMillis = 60L * 60_000L
        )
        return buildComplicationData(
            type = type,
            state = previewState,
            tapIntent = null
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        TimerForegroundService.refreshRemainingTimeFromClock()
        val state = TimerForegroundService.timerState.value
        return buildComplicationData(
            type = request.complicationType,
            state = state,
            tapIntent = createTapPendingIntent()
        )
    }

    private fun buildComplicationData(
        type: ComplicationType,
        state: TimerState,
        tapIntent: PendingIntent?
    ): ComplicationData? {
        val phaseLabel = when (state.phase) {
            TimerPhase.WORK -> getString(R.string.phase_work)
            TimerPhase.BREAK -> getString(R.string.phase_break)
            TimerPhase.READINESS -> getString(R.string.phase_readiness)
        }
        val titleLabel = if (!state.isRunning && !state.isAwaitingManualContinue) {
            getString(R.string.hint_paused)
        } else {
            phaseLabel
        }

        val titleText = PlainComplicationText.Builder(titleLabel).build()
        val contentDescription = PlainComplicationText.Builder(
            "$phaseLabel ${state.formattedTime()}"
        ).build()

        val shortTimeText: ComplicationText = when {
            state.phase == TimerPhase.READINESS && state.totalPhaseMillis <= 0L ->
                PlainComplicationText.Builder(getString(R.string.phase_readiness)).build()
            state.isRunning && state.remainingMillis > 0L -> {
                val targetInstant = Instant.ofEpochMilli(
                    System.currentTimeMillis() + state.remainingMillis
                )
                TimeDifferenceComplicationText.Builder(
                    TimeDifferenceStyle.STOPWATCH,
                    CountDownTimeReference(targetInstant)
                ).build()
            }
            else -> PlainComplicationText.Builder(state.compactPillTime()).build()
        }

        val longTimeText: ComplicationText = when {
            state.phase == TimerPhase.READINESS && state.totalPhaseMillis <= 0L ->
                PlainComplicationText.Builder(getString(R.string.ongoing_readiness_status)).build()
            state.isRunning && state.remainingMillis > 0L -> {
                val targetInstant = Instant.ofEpochMilli(
                    System.currentTimeMillis() + state.remainingMillis
                )
                TimeDifferenceComplicationText.Builder(
                    TimeDifferenceStyle.STOPWATCH,
                    CountDownTimeReference(targetInstant)
                )
                    .setText("$phaseLabel: ^1")
                    .build()
            }
            else -> PlainComplicationText.Builder("$phaseLabel: ${state.formattedTime()}").build()
        }

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = shortTimeText,
                    contentDescription = contentDescription
                )
                    .setTitle(titleText)
                    .setTapAction(tapIntent)
                    .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = longTimeText,
                    contentDescription = contentDescription
                )
                    .setTitle(titleText)
                    .setTapAction(tapIntent)
                    .build()

            ComplicationType.RANGED_VALUE -> {
                val progressPercent = (state.progressFraction().coerceIn(0.0f, 1.0f) * 100.0f)
                RangedValueComplicationData.Builder(
                    value = progressPercent,
                    min = 0.0f,
                    max = 100.0f,
                    contentDescription = contentDescription
                )
                    .setText(shortTimeText)
                    .setTitle(titleText)
                    .setTapAction(tapIntent)
                    .build()
            }

            else -> null
        }
    }

    private fun createTapPendingIntent(): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            COMPLICATION_TAP_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val COMPLICATION_TAP_REQUEST_CODE = 3001

        // Requests an immediate refresh of all active FocusMesh watch face complication slots.
        fun requestComplicationUpdate(context: Context) {
            runCatching {
                val component = ComponentName(context, FocusTimerComplicationService::class.java)
                ComplicationDataSourceUpdateRequester.create(context, component).requestUpdateAll()
            }
        }
    }
}
