package com.focusmesh.wear.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

// Foreground service maintaining the continuous Work -> Break -> Readiness cycle.
// Renders the live MM:SS countdown directly into the OngoingActivity Icon and Status so the watch face
// indicator displays the current time rather than a static alarm icon.
class TimerForegroundService : LifecycleService() {

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var alertController: AlertController
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    private var latestPreferences = UserPreferences()
    private var tickerJob: Job? = null
    private var targetCompletionElapsedMillis: Long = 0L
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
                latestPreferences = prefs
                val current = _timerState.value
                if (!current.isRunning && current.remainingMillis == current.totalPhaseMillis) {
                    val updatedTotal = phaseDurationMillis(current.phase, prefs)
                    _timerState.value = current.copy(
                        remainingMillis = updatedTotal,
                        totalPhaseMillis = updatedTotal,
                        isAwaitingManualContinue = current.phase == TimerPhase.READINESS && !prefs.readinessGraceEnabled
                    )
                } else if (current.phase == TimerPhase.READINESS && oldPrefs.readinessGraceEnabled != prefs.readinessGraceEnabled) {
                    enterPhase(TimerPhase.READINESS, autoStart = prefs.readinessGraceEnabled, triggerAlerts = false)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE_OR_CONTINUE -> handlePrimaryAction()
            ACTION_SKIP_PHASE -> advanceToNextPhase(triggerAlerts = true)
            ACTION_RESET_PHASE -> resetCurrentPhase()
            ACTION_ALARM_EXPIRED -> onExactAlarmExpired()
        }
        return START_STICKY
    }

    // Handles tap on the central time element or hardware stem button press depending on the active phase.
    private fun handlePrimaryAction() {
        val current = _timerState.value
        if (current.phase == TimerPhase.READINESS) {
            enterPhase(TimerPhase.WORK, autoStart = true, triggerAlerts = true)
            return
        }
        if (current.isRunning) {
            pauseTimer()
        } else {
            startOrResumeTimer()
        }
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
            isAwaitingManualContinue = false
        )
        scheduleExactWakeup(targetCompletionElapsedMillis)
        startForegroundWithOngoingActivity()
        startUiTicker()
    }

    private fun pauseTimer() {
        tickerJob?.cancel()
        cancelExactWakeup()
        val remaining = (targetCompletionElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _timerState.value = _timerState.value.copy(
            isRunning = false,
            remainingMillis = remaining
        )
        updateOngoingNotification()
    }

    private fun resetCurrentPhase() {
        tickerJob?.cancel()
        cancelExactWakeup()
        val phase = _timerState.value.phase
        val duration = phaseDurationMillis(phase, latestPreferences)
        _timerState.value = TimerState(
            phase = phase,
            isRunning = false,
            remainingMillis = duration,
            totalPhaseMillis = duration,
            isAwaitingManualContinue = phase == TimerPhase.READINESS && !latestPreferences.readinessGraceEnabled
        )
        updateOngoingNotification()
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
        } else {
            advanceToNextPhase(triggerAlerts = true)
        }
    }

    private fun advanceToNextPhase(triggerAlerts: Boolean) {
        val next = _timerState.value.phase.nextPhase()
        val shouldAutoStart = when (next) {
            TimerPhase.WORK -> true
            TimerPhase.BREAK -> true
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

        val duration = phaseDurationMillis(phase, latestPreferences)
        val awaitingManual = phase == TimerPhase.READINESS && !latestPreferences.readinessGraceEnabled

        if (autoStart && duration > 0L) {
            targetCompletionElapsedMillis = SystemClock.elapsedRealtime() + duration
            _timerState.value = TimerState(
                phase = phase,
                isRunning = true,
                remainingMillis = duration,
                totalPhaseMillis = duration,
                isAwaitingManualContinue = false
            )
            scheduleExactWakeup(targetCompletionElapsedMillis)
            startForegroundWithOngoingActivity()
            startUiTicker()
        } else {
            _timerState.value = TimerState(
                phase = phase,
                isRunning = false,
                remainingMillis = duration,
                totalPhaseMillis = duration,
                isAwaitingManualContinue = awaitingManual
            )
            startForegroundWithOngoingActivity()
        }
    }

    private fun startUiTicker() {
        tickerJob?.cancel()
        lastNotificationSecond = -1L
        tickerJob = lifecycleScope.launch {
            while (_timerState.value.isRunning) {
                val now = SystemClock.elapsedRealtime()
                val remaining = (targetCompletionElapsedMillis - now).coerceAtLeast(0L)
                _timerState.value = _timerState.value.copy(remainingMillis = remaining)
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

    private fun phaseDurationMillis(phase: TimerPhase, prefs: UserPreferences): Long = when (phase) {
        TimerPhase.WORK -> prefs.workDurationMinutes * 60L * 1000L
        TimerPhase.BREAK -> prefs.breakDurationMinutes * 60L * 1000L
        TimerPhase.READINESS -> if (prefs.readinessGraceEnabled) {
            prefs.readinessGraceMinutes * 60L * 1000L
        } else {
            0L
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

    // Builds the ongoing notification using a dynamically rendered MM:SS bitmap Icon so the watch face
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
            TimerPhase.WORK -> getString(R.string.ongoing_work_status, formattedTime)
            TimerPhase.BREAK -> getString(R.string.ongoing_break_status, formattedTime)
            TimerPhase.READINESS -> if (state.totalPhaseMillis > 0L) {
                formattedTime
            } else {
                getString(R.string.ongoing_readiness_status)
            }
        }

        val dynamicTimerIcon = OngoingPillIconRenderer.createTimerIcon(
            state = state,
            readinessLabel = readinessShortLabel
        )
        val compatTimerIcon = IconCompat.createFromIcon(this, dynamicTimerIcon)

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
        return builder.build()
    }

    companion object {
        const val ACTION_TOGGLE_OR_CONTINUE = "com.focusmesh.wear.action.TOGGLE_OR_CONTINUE"
        const val ACTION_SKIP_PHASE = "com.focusmesh.wear.action.SKIP_PHASE"
        const val ACTION_RESET_PHASE = "com.focusmesh.wear.action.RESET_PHASE"
        const val ACTION_ALARM_EXPIRED = "com.focusmesh.wear.action.ALARM_EXPIRED"

        private const val CHANNEL_ID = "focus_mesh_ongoing_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    }
}
