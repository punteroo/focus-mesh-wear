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
// Publishes a Wear OS OngoingActivity so One UI Watch renders the floating status island on the watch face.
class TimerForegroundService : LifecycleService() {

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var alertController: AlertController
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    private var latestPreferences = UserPreferences()
    private var tickerJob: Job? = null
    private var targetCompletionElapsedMillis: Long = 0L

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
                // If the timer is idle at the start of a phase, sync the displayed duration with updated settings.
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

    // Handles single-tap or hardware stem button press depending on the active phase.
    private fun handlePrimaryAction() {
        val current = _timerState.value
        if (current.phase == TimerPhase.READINESS) {
            // In Readiness, manual confirmation starts the next 60-minute Work focus block immediately.
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
            // When the optional Readiness grace timer expires, emit a reminder alert and hold for manual start.
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
            // Readiness requires manual continue unless the optional grace countdown is enabled.
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
        tickerJob = lifecycleScope.launch {
            while (_timerState.value.isRunning) {
                val now = SystemClock.elapsedRealtime()
                val remaining = (targetCompletionElapsedMillis - now).coerceAtLeast(0L)
                _timerState.value = _timerState.value.copy(remainingMillis = remaining)
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
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setBypassDnd(true)
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

    // Builds the notification and attaches a Wear OS OngoingActivity for the One UI Watch home screen island.
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

        val statusText = when (state.phase) {
            TimerPhase.WORK -> getString(R.string.ongoing_work_status, state.formattedTime())
            TimerPhase.BREAK -> getString(R.string.ongoing_break_status, state.formattedTime())
            TimerPhase.READINESS -> getString(R.string.ongoing_readiness_status)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)

        val ongoingStatus = if (state.isRunning && state.remainingMillis > 0L) {
            Status.Builder()
                .addTemplate("#phase# #time#")
                .addPart("phase", Status.TextPart(state.phase.name))
                .addPart("time", Status.TimerPart(SystemClock.elapsedRealtime() + state.remainingMillis))
                .build()
        } else {
            Status.Builder()
                .addTemplate("#status#")
                .addPart("status", Status.TextPart(statusText))
                .build()
        }

        val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setAnimatedIcon(android.R.drawable.ic_lock_idle_alarm)
            .setStaticIcon(android.R.drawable.ic_lock_idle_alarm)
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

        private const val CHANNEL_ID = "focus_mesh_alarm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    }
}
