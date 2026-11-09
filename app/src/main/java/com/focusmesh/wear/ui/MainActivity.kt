package com.focusmesh.wear.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import com.focusmesh.wear.ui.screens.SettingsDrawerScreen
import com.focusmesh.wear.ui.screens.TimerScreen
import kotlinx.coroutines.launch

// Entry activity hosting the uncluttered timer canvas (Page 0) and right-side configuration drawer (Page 1).
// Observes both Wear OS Ambient Mode and Android Battery Saver state so mesh rendering drops to a solid
// background automatically when Battery Saver is active.
class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()
    private var isAmbientState by mutableStateOf(false)
    private var isBatterySaverState by mutableStateOf(false)

    private lateinit var powerManager: PowerManager
    private lateinit var ambientObserver: AmbientLifecycleObserver

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val ambientCallbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbientState = true
            viewModel.onAmbientUpdate()
        }

        override fun onExitAmbient() {
            isAmbientState = false
            viewModel.onAmbientUpdate()
        }

        override fun onUpdateAmbient() {
            // Synchronously refresh the remaining minutes on every 1-minute Wear OS ambient wakeup.
            viewModel.onAmbientUpdate()
        }
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                isBatterySaverState = powerManager.isPowerSaveMode
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatterySaverState = powerManager.isPowerSaveMode

        ambientObserver = AmbientLifecycleObserver(this, ambientCallbacks)
        lifecycle.addObserver(ambientObserver)

        registerReceiver(
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )

        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                val timerState by viewModel.timerState.collectAsState()
                val preferences by viewModel.preferences.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { 2 }
                )

                // Stop any active sound preview when returning from the settings drawer to the main timer.
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage == 0) {
                        viewModel.stopPreviewSound()
                    }
                }

                // When the configuration drawer (Page 1) is open, Back returns to the main timer (Page 0).
                BackHandler(enabled = pagerState.currentPage == 1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }

                // Disable full-screen pager drag on Page 0 so only a swipe starting from the right edge
                // of TimerScreen opens Settings, allowing unrestricted mesh interaction across the screen.
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerState.currentPage == 1 && !isAmbientState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (page == 0) {
                        TimerScreen(
                            timerState = timerState,
                            preferences = preferences,
                            isBatterySaverActive = isBatterySaverState,
                            isAmbient = isAmbientState,
                            onPrimaryTap = viewModel::onPrimaryAction,
                            onLongPressSkip = viewModel::onSkipPhase,
                            onOpenSettingsFromRightEdge = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )
                    } else {
                        SettingsDrawerScreen(
                            preferences = preferences,
                            isBatterySaverActive = isBatterySaverState,
                            onCycleBackgroundMode = viewModel::cycleBackgroundMode,
                            onToggleTouchWave = viewModel::setTouchWaveEnabled,
                            onToggleReadinessGrace = viewModel::setReadinessGraceEnabled,
                            onSaveWorkDurationSeconds = viewModel::setWorkDurationSeconds,
                            onSaveBreakDurationSeconds = viewModel::setBreakDurationSeconds,
                            onSaveGraceDurationSeconds = viewModel::setReadinessGraceSeconds,
                            onCycleFont = viewModel::cycleFontChoice,
                            onCycleAlertMode = viewModel::cycleAlertMode,
                            onCycleSoundOption = viewModel::cycleSoundOption,
                            onVolumeChange = viewModel::setAlertVolume,
                            onPreviewAlert = viewModel::previewAlert,
                            onSkipPhase = viewModel::onSkipPhase,
                            onResetPhase = viewModel::onResetPhase
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isBatterySaverState = powerManager.isPowerSaveMode
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerSaveReceiver) }
        super.onDestroy()
    }

    // Intercepts secondary hardware watch stem buttons only while MainActivity has foreground window focus.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_PRIMARY,
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2 -> {
                if (event?.repeatCount == 0) {
                    viewModel.onPrimaryAction()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
