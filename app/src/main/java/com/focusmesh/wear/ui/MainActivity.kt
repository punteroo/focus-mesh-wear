package com.focusmesh.wear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

// Entry activity hosting the uncluttered timer canvas and left-swipe configuration drawer.
// Observes Wear OS Ambient Mode to halt 3D mesh animations when the watch enters Always-On Display.
class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()
    private var isAmbientState by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val ambientCallbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbientState = true
        }

        override fun onExitAmbient() {
            isAmbientState = false
        }

        override fun onUpdateAmbient() {
            // Static ambient frame requires no continuous animation updates.
        }
    }

    private lateinit var ambientObserver: AmbientLifecycleObserver

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ambientObserver = AmbientLifecycleObserver(this, ambientCallbacks)
        lifecycle.addObserver(ambientObserver)

        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                val timerState by viewModel.timerState.collectAsState()
                val preferences by viewModel.preferences.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                // Page 1 is the main TimerScreen; Page 0 (to the left) is the SettingsDrawerScreen.
                // Swiping from the left edge pulls Page 0 into view without triggering system back dismissal.
                val pagerState = rememberPagerState(
                    initialPage = 1,
                    pageCount = { 2 }
                )

                // When the configuration drawer (Page 0) is open, Back returns to the main timer (Page 1).
                BackHandler(enabled = pagerState.currentPage == 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !isAmbientState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (page == 0) {
                        SettingsDrawerScreen(
                            preferences = preferences,
                            onToggleMesh3d = viewModel::setMesh3dEnabled,
                            onToggleTouchWave = viewModel::setTouchWaveEnabled,
                            onToggleReadinessGrace = viewModel::setReadinessGraceEnabled,
                            onCycleGraceMinutes = viewModel::cycleReadinessGraceMinutes,
                            onCycleWorkMinutes = viewModel::cycleWorkDurationMinutes,
                            onCycleBreakMinutes = viewModel::cycleBreakDurationMinutes,
                            onCycleFont = viewModel::cycleFontChoice,
                            onCycleAlertMode = viewModel::cycleAlertMode,
                            onCycleSoundOption = viewModel::cycleSoundOption,
                            onVolumeChange = viewModel::setAlertVolume,
                            onPreviewAlert = viewModel::previewAlert,
                            onSkipPhase = viewModel::onSkipPhase,
                            onResetPhase = viewModel::onResetPhase
                        )
                    } else {
                        TimerScreen(
                            timerState = timerState,
                            preferences = preferences,
                            isAmbient = isAmbientState,
                            onPrimaryTap = viewModel::onPrimaryAction,
                            onLongPressSkip = viewModel::onSkipPhase
                        )
                    }
                }
            }
        }
    }

    // Intercepts secondary hardware watch stem buttons only while MainActivity has foreground window focus.
    // This never overrides the user's system-wide Galaxy Watch Ultra Quick Button binding outside the app.
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
