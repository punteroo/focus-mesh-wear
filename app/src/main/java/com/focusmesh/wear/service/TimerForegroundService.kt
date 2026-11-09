package com.focusmesh.wear.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.focusmesh.wear.R
import com.focusmesh.wear.alerts.AlertController
import com.focusmesh.wear.data.PreferencesRepository
import com.focusmesh.wear.domain.model.RoutineMode
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.TimerState
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Foreground service maintaining both Standard sequential cycles and Flexible dual-pool Work/Break swapping.
class TimerForegroundService : LifecycleService() {

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var alertController: AlertController
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    private var latestPreferences = UserPreferences()
    private var hasLoadedInitialPreferences = false
    private var tickerJob: Job? = null
    private var lastNotificationSecond: Long = -1L

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(applicationContext)
        alertController = AlertController(applicationContext)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createAlarmNotificationChannel()

        lifecycleScope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                val oldPrefs = latestPreferences
                val oldPreset = oldPrefs.activePresetForMode(oldPrefs.routineMode)
                val newPreset = prefs.activePresetForMode(prefs.routineMode)
                latestPreferences = prefs

                if (!hasLoadedInitialPreferences) {
                    hasLoadedInitialPreferences = true
                    if (!_timerState.value.isRunning) {
                        resetToActivePreset(prefs)
                    }
                    return@collectLatest
                }

                val modeChanged = oldPrefs.routineMode != prefs.routineMode
                val presetIdChanged = oldPreset.id != newPreset.id
                val presetDurationsChanged =
                    oldPreset.workDurationSeconds != newPreset.workDurationSeconds ||
                        oldPreset.breakDurationSeconds != newPreset.breakDurationSeconds

                // Swapping modes or selecting a different preset immediately resets timers.
                if (modeChanged || presetIdChanged || presetDurationsChanged) {
                    resetToActivePreset(prefs)
                    return@collectLatest
                }

                // Update preset display name live if only the name was edited.
                if (oldPreset.name != newPreset.name) {
                    _timerState.value = _timerState.value.copy(activePresetName = newPreset.name)
                    FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
                }

                val current = _timerState.value
                if (current.phase == TimerPhase.READINESS &&
                    oldPrefs.readinessGraceEnabled != prefs.readinessGraceEnabled
                ) {
                    enterPhase(
                        phase = TimerPhase.READINESS,
                        autoStart = prefs.readinessGraceEnabled,
                        triggerAlerts = false
                    )
                }

                if (oldPrefs.ongoingPillEnabled != prefs.ongoingPillEnabled && current.isRunning) {
                    notificationManager.cancel(NOTIFICATION_ID)
                    startForegroundWithOngoingActivity()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE_OR_CONTINUE -> handlePrimaryAction()
            ACTION_SKIP_PHASE -> handleSkipOrSwapPhase()
            ACTION_SWAP_FLEXIBLE_PHASE -> swapFlexibleWorkAndBreak(triggerAlerts = false)
            ACTION_RESET_PHASE -> resetToActivePreset(latestPreferences)
            ACTION_ALARM_EXPIRED -> onExactAlarmExpired()
            ACTION_AMBIENT_UPDATE -> onAmbientTickUpdate()
        }
        return START_STICKY
    }

    // Handles tap on the central time element or hardware stem button press depending on the active phase.
    private fun handlePrimaryAction() {
        val current = _timerState.value
        if (current.phase == TimerPhase.READINESS) {
            replenishPoolsAndStartWork(triggerAlerts = true)
            return
        }
        if (current.isRunning) {
            pauseTimer()
        } else {
            startOrResumeTimer()
        }
    }

    private fun handleSkipOrSwapPhase() {
        if (latestPreferences.routineMode == RoutineMode.FLEXIBLE &&
            _timerState.value.phase != TimerPhase.READINESS
        ) {
            swapFlexibleWorkAndBreak(triggerAlerts = false)
        } else {
            advanceToNextPhase(triggerAlerts = true)
        }
    }

    // In Flexible Mode, swaps between WORK and BREAK while pausing and preserving each timer's remaining pool.
    private fun swapFlexibleWorkAndBreak(triggerAlerts: Boolean) {
        val current = _timerState.value
        if (current.phase == TimerPhase.READINESS) {
            replenishPoolsAndStartWork(triggerAlerts = triggerAlerts)
            return
        }

        val liveRemaining = if (current.isRunning) {
            (targetCompletionElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            current.remainingMillis
        }

        tickerJob?.cancel()
        cancelExactWakeup()

        if (triggerAlerts) {
            alertController.triggerPhaseAlert(
                newPhase = if (current.phase == TimerPhase.WORK) TimerPhase.BREAK else TimerPhase.WORK,
                preferences = latestPreferences
            )
        }

        val preset = latestPreferences.activePresetForMode(RoutineMode.FLEXIBLE)
        val fullWorkMillis = preset.workDurationSeconds.coerceAtLeast(1) * 1000L
        val fullBreakMillis = preset.breakDurationSeconds.coerceAtLeast(1) * 1000L

        if (current.phase == TimerPhase.WORK) {
            val savedWork = if (liveRemaining > 0L) liveRemaining else fullWorkMillis
            val nextBreakRemaining = if (current.savedBreakRemainingMillis > 0L) {
                current.savedBreakRemainingMillis
            } else {
                fullBreakMillis
            }
            _timerState.value = current.copy(
                routineMode = RoutineMode.FLEXIBLE,
                activePresetId = preset.id,
                activePresetName = preset.name,
                phase = TimerPhase.BREAK,
                isRunning = false,
                remainingMillis = nextBreakRemaining,
                totalPhaseMillis = current.savedBreakTotalMillis.coerceAtLeast(fullBreakMillis),
                savedWorkRemainingMillis = savedWork,
                savedWorkTotalMillis = fullWorkMillis,
                savedBreakRemainingMillis = nextBreakRemaining,
                savedBreakTotalMillis = fullBreakMillis,
                isAwaitingManualContinue = false
            )
        } else {
            val savedBreak = if (liveRemaining > 0L) liveRemaining else fullBreakMillis
            val nextWorkRemaining = if (current.savedWorkRemainingMillis > 0L) {
                current.savedWorkRemainingMillis
            } else {
                fullWorkMillis
            }
            _timerState.value = current.copy(
                routineMode = RoutineMode.FLEXIBLE,
                activePresetId = preset.id,
                activePresetName = preset.name,
                phase = TimerPhase.WORK,
                isRunning = false,
                remainingMillis = nextWorkRemaining,
                totalPhaseMillis = current.savedWorkTotalMillis.coerceAtLeast(fullWorkMillis),
                savedWorkRemainingMillis = nextWorkRemaining,
                savedWorkTotalMillis = fullWorkMillis,
                savedBreakRemainingMillis = savedBreak,
                savedBreakTotalMillis = fullBreakMillis,
                isAwaitingManualContinue = false
            )
        }

        updateOngoingNotification()
        FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
    }

    private fun startOrResumeTimer() {
        val current = _timerState.value
        val remaining = if (current.remainingMillis <= 0L) {
            phaseDurationMillis(current.phase, latestPreferences)
        } else {
            current.remainingMillis
        }
        targetCompletionElapsedMillis = SystemClock.elapsedRealtime() + remaining
        _timerState.value = current.copy(
            isRunning = true,
            remainingMillis = remaining,
            savedWorkRemainingMillis = if (current.phase == TimerPhase.WORK) remaining else current.savedWorkRemainingMillis,
            savedBreakRemainingMillis = if (current.phase == TimerPhase.BREAK) remaining else current.savedBreakRemainingMillis,
            isAwaitingManualContinue = false
        )
        scheduleExactWakeup(targetCompletionElapsedMillis)
        startForegroundWithOngoingActivity()
        FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
        startUiTicker()
    }

    private fun pauseTimer() {
        tickerJob?.cancel()
        cancelExactWakeup()
        val current = _timerState.value
        val remaining = (targetCompletionElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _timerState.value = current.copy(
            isRunning = false,
            remainingMillis = remaining,
            savedWorkRemainingMillis = if (current.phase == TimerPhase.WORK) remaining else current.savedWorkRemainingMillis,
            savedBreakRemainingMillis = if (current.phase == TimerPhase.BREAK) remaining else current.savedBreakRemainingMillis
        )
        updateOngoingNotification()
        FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
    }

    // Resets both Work and Break pools to the active preset's full configured durations in the PAUSED state.
    private fun resetToActivePreset(prefs: UserPreferences) {
        tickerJob?.cancel()
        cancelExactWakeup()
        val preset = prefs.activePresetForMode(prefs.routineMode)
        val workMillis = preset.workDurationSeconds.coerceAtLeast(1) * 1000L
        val breakMillis = preset.breakDurationSeconds.coerceAtLeast(1) * 1000L
        _timerState.value = TimerState(
            routineMode = prefs.routineMode,
            activePresetId = preset.id,
            activePresetName = preset.name,
            phase = TimerPhase.WORK,
            isRunning = false,
            remainingMillis = workMillis,
            totalPhaseMillis = workMillis,
            savedWorkRemainingMillis = workMillis,
            savedWorkTotalMillis = workMillis,
            savedBreakRemainingMillis = breakMillis,
            savedBreakTotalMillis = breakMillis,
            isAwaitingManualContinue = false
        )
        updateOngoingNotification()
        FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
    }

    private fun replenishPoolsAndStartWork(triggerAlerts: Boolean) {
        val preset = latestPreferences.activePresetForMode(latestPreferences.routineMode)
        val workMillis = preset.workDurationSeconds.coerceAtLeast(1) * 1000L
        val breakMillis = preset.breakDurationSeconds.coerceAtLeast(1) * 1000L
        _timerState.value = _timerState.value.copy(
            routineMode = latestPreferences.routineMode,
            activePresetId = preset.id,
            activePresetName = preset.name,
            savedWorkRemainingMillis = workMillis,
            savedWorkTotalMillis = workMillis,
            savedBreakRemainingMillis = breakMillis,
            savedBreakTotalMillis = breakMillis
        )
        enterPhase(TimerPhase.WORK, autoStart = true, triggerAlerts = triggerAlerts)
    }

    private fun onExactAlarmExpired() {
        val current = _timerState.value
        if (current.phase == TimerPhase.READINESS && latestPreferences.readinessGraceEnabled) {
            tickerJob?.cancel()
            alertController.triggerPhaseAlert(TimerPhase.READINESS, latestPreferences)
            _timerState.value = current.copy(
                isRunning = false,
                remainingMillis = 0L,
                isAwaitingManualContinue = true
            )
            updateOngoingNotification()
            FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
            return
        }

        if (latestPreferences.routineMode == RoutineMode.FLEXIBLE) {
            if (current.phase == TimerPhase.WORK) {
                _timerState.value = current.copy(remainingMillis = 0L, savedWorkRemainingMillis = 0L)
                if (current.savedBreakRemainingMillis > 0L) {
                    swapFlexibleWorkAndBreak(triggerAlerts = true)
                } else {
                    enterPhase(
                        phase = TimerPhase.READINESS,
                        autoStart = latestPreferences.readinessGraceEnabled,
                        triggerAlerts = true
                    )
                }
            } else if (current.phase == TimerPhase.BREAK) {
                _timerState.value = current.copy(remainingMillis = 0L, savedBreakRemainingMillis = 0L)
                if (current.savedWorkRemainingMillis > 0L) {
                    swapFlexibleWorkAndBreak(triggerAlerts = true)
                } else {
                    enterPhase(
                        phase = TimerPhase.READINESS,
                        autoStart = latestPreferences.readinessGraceEnabled,
                        triggerAlerts = true
                    )
                }
            } else {
                replenishPoolsAndStartWork(triggerAlerts = true)
            }
        } else {
            advanceToNextPhase(triggerAlerts = true)
        }
    }

    private fun advanceToNextPhase(triggerAlerts: Boolean) {
        val next = _timerState.value.phase.nextPhase()
        val shouldAutoStart = when (next) {
            TimerPhase.WORK -> true
            // Break timer enters paused state by default so the user can manually start their break when ready.
            TimerPhase.BREAK -> false
            TimerPhase.READINESS -> latestPreferences.readinessGraceEnabled
        }
        enterPhase(next, autoStart = shouldAutoStart, triggerAlerts = triggerAlerts)
    }

    private fun enterPhase(phase: TimerPhase, autoStart: Boolean, triggerAlerts: Boolean) {
        tickerJob?.cancel()
        cancelExactWakeup()

        if (triggerAlerts) {
            alertController.triggerPhaseAlert(phase, latestPreferences)
        }

        val preset = latestPreferences.activePresetForMode(latestPreferences.routineMode)
        val workMillis = preset.workDurationSeconds.coerceAtLeast(1) * 1000L
        val breakMillis = preset.breakDurationSeconds.coerceAtLeast(1) * 1000L
        val duration = phaseDurationMillis(phase, latestPreferences)
        val awaitingManual = phase == TimerPhase.READINESS && !latestPreferences.readinessGraceEnabled

        if (autoStart && duration > 0L) {
            targetCompletionElapsedMillis = SystemClock.elapsedRealtime() + duration
            _timerState.value = TimerState(
                routineMode = latestPreferences.routineMode,
                activePresetId = preset.id,
                activePresetName = preset.name,
                phase = phase,
                isRunning = true,
                remainingMillis = duration,
                totalPhaseMillis = duration,
                savedWorkRemainingMillis = workMillis,
                savedWorkTotalMillis = workMillis,
                savedBreakRemainingMillis = breakMillis,
                savedBreakTotalMillis = breakMillis,
                isAwaitingManualContinue = false
            )
            scheduleExactWakeup(targetCompletionElapsedMillis)
            startForegroundWithOngoingActivity()
            FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
            startUiTicker()
        } else {
            _timerState.value = TimerState(
                routineMode = latestPreferences.routineMode,
                activePresetId = preset.id,
                activePresetName = preset.name,
                phase = phase,
                isRunning = false,
                remainingMillis = duration,
                totalPhaseMillis = duration,
                savedWorkRemainingMillis = if (phase == TimerPhase.WORK) duration else _timerState.value.savedWorkRemainingMillis,
                savedWorkTotalMillis = workMillis,
                savedBreakRemainingMillis = if (phase == TimerPhase.BREAK) duration else _timerState.value.savedBreakRemainingMillis,
                savedBreakTotalMillis = breakMillis,
                isAwaitingManualContinue = awaitingManual
            )
            startForegroundWithOngoingActivity()
            FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
        }
    }

    private fun startUiTicker() {
        tickerJob?.cancel()
        lastNotificationSecond = -1L
        tickerJob = lifecycleScope.launch {
            while (_timerState.value.isRunning) {
                val now = SystemClock.elapsedRealtime()
                val remaining = (targetCompletionElapsedMillis - now).coerceAtLeast(0L)
                val current = _timerState.value
                _timerState.value = current.copy(
                    remainingMillis = remaining,
                    savedWorkRemainingMillis = if (current.phase == TimerPhase.WORK) remaining else current.savedWorkRemainingMillis,
                    savedBreakRemainingMillis = if (current.phase == TimerPhase.BREAK) remaining else current.savedBreakRemainingMillis
                )
                val currentSecond = (remaining + 999L) / 1000L
                if (currentSecond != lastNotificationSecond) {
                    lastNotificationSecond = currentSecond
                    updateOngoingNotification()
                }
                if (remaining <= 0L) {
                    onExactAlarmExpired()
                    break
                }
                delay(250L)
            }
        }
    }

    // Invoked during Wear OS 1-minute Ambient Mode wakeups or when exiting Ambient Mode so both the
    // UI state and ongoing watch face notification stay synchronized with SystemClock.elapsedRealtime().
    private fun onAmbientTickUpdate() {
        val current = _timerState.value
        if (!current.isRunning) return
        val remaining = refreshRemainingTimeFromClock()
        if (remaining <= 0L) {
            onExactAlarmExpired()
        } else {
            updateOngoingNotification()
            FocusTimerComplicationService.requestComplicationUpdate(applicationContext)
        }
    }

    private fun phaseDurationMillis(phase: TimerPhase, prefs: UserPreferences): Long {
        val preset = prefs.activePresetForMode(prefs.routineMode)
        return when (phase) {
            TimerPhase.WORK -> preset.workDurationSeconds.coerceAtLeast(1) * 1000L
            TimerPhase.BREAK -> preset.breakDurationSeconds.coerceAtLeast(1) * 1000L
            TimerPhase.READINESS -> if (prefs.readinessGraceEnabled) {
                prefs.readinessGraceSeconds.coerceAtLeast(1) * 1000L
            } else {
                0L
            }
        }
    }

    private fun scheduleExactWakeup(triggerAtElapsedMillis: Long) {
        val pendingIntent = alarmPendingIntent()
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedMillis,
                pendingIntent
            )
        }
    }

    private fun cancelExactWakeup() {
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createAlarmNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setBypassDnd(true)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundWithOngoingActivity() {
        val notification = buildOngoingNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateOngoingNotification() {
        val notification = buildOngoingNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Builds the ongoing notification using a dynamically rendered countdown bitmap Icon so the watch face
    // OngoingActivity indicator displays the live countdown time instead of a static alarm icon.
    private fun buildOngoingNotification(): Notification {
        val state = _timerState.value
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTime = state.formattedTime()
        val readinessShortLabel = getString(R.string.phase_readiness)
        val statusText = when (state.phase) {
            TimerPhase.WORK -> "${state.activePresetName}: $formattedTime"
            TimerPhase.BREAK -> getString(R.string.ongoing_break_status, formattedTime)
            TimerPhase.READINESS -> if (state.totalPhaseMillis > 0L) {
                formattedTime
            } else {
                getString(R.string.ongoing_readiness_status)
            }
        }

        val timerBitmap = OngoingPillIconRenderer.renderTimerBitmap(
            state = state,
            readinessLabel = readinessShortLabel
        )
        val dynamicTimerIcon = Icon.createWithBitmap(timerBitmap)
        val compatTimerIcon = IconCompat.createWithBitmap(timerBitmap)

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val hasCountdown = state.remainingMillis > 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(compatTimerIcon)
            .setContentTitle(formattedTime)
            .setContentText(statusText)
            .setSubText(formattedTime)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (state.isRunning && hasCountdown) {
            builder
                .setWhen(nowWall + state.remainingMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder
                .setShowWhen(false)
                .setUsesChronometer(false)
        }

        if (latestPreferences.ongoingPillEnabled) {
            val ongoingStatus = when {
                state.isRunning && hasCountdown -> {
                    Status.forPart(Status.TimerPart(nowElapsed + state.remainingMillis))
                }
                !state.isRunning && hasCountdown -> {
                    Status.forPart(
                        Status.TimerPart(
                            nowElapsed + state.remainingMillis,
                            nowElapsed
                        )
                    )
                }
                else -> {
                    Status.forPart(Status.TextPart(readinessShortLabel))
                }
            }

            val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
                .setStaticIcon(dynamicTimerIcon)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setTitle(formattedTime)
                .setTouchIntent(contentPendingIntent)
                .setStatus(ongoingStatus)
                .build()

            ongoingActivity.apply(applicationContext)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_TOGGLE_OR_CONTINUE = "com.focusmesh.wear.action.TOGGLE_OR_CONTINUE"
        const val ACTION_SKIP_PHASE = "com.focusmesh.wear.action.SKIP_PHASE"
        const val ACTION_SWAP_FLEXIBLE_PHASE = "com.focusmesh.wear.action.SWAP_FLEXIBLE_PHASE"
        const val ACTION_RESET_PHASE = "com.focusmesh.wear.action.RESET_PHASE"
        const val ACTION_ALARM_EXPIRED = "com.focusmesh.wear.action.ALARM_EXPIRED"
        const val ACTION_AMBIENT_UPDATE = "com.focusmesh.wear.action.AMBIENT_UPDATE"

        private const val CHANNEL_ID = "focus_mesh_ongoing_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001

        @Volatile
        private var targetCompletionElapsedMillis: Long = 0L

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        // Synchronously recalculates the remaining milliseconds from SystemClock.elapsedRealtime()
        // so that MainActivity.onUpdateAmbient() immediately updates the Compose state before
        // Wear OS latches the display frame and suspends the CPU again.
        fun refreshRemainingTimeFromClock(): Long {
            val current = _timerState.value
            if (!current.isRunning) return current.remainingMillis
            val remaining = (targetCompletionElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            _timerState.value = current.copy(
                remainingMillis = remaining,
                savedWorkRemainingMillis = if (current.phase == TimerPhase.WORK) remaining else current.savedWorkRemainingMillis,
                savedBreakRemainingMillis = if (current.phase == TimerPhase.BREAK) remaining else current.savedBreakRemainingMillis
            )
            return remaining
        }
    }
}
