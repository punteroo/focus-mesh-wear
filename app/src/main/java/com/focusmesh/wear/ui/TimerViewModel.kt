package com.focusmesh.wear.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusmesh.wear.alerts.AlertController
import com.focusmesh.wear.data.PreferencesRepository
import com.focusmesh.wear.domain.model.AlertMode
import com.focusmesh.wear.domain.model.BackgroundMode
import com.focusmesh.wear.domain.model.FontChoice
import com.focusmesh.wear.domain.model.RoutineMode
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.TimerPreset
import com.focusmesh.wear.domain.model.TimerState
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.service.TimerForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Bridges the Wear OS Compose presentation layer with the foreground timer service and DataStore repository.
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferencesRepository = PreferencesRepository(appContext)
    private val alertController = AlertController(appContext)

    val timerState: StateFlow<TimerState> = TimerForegroundService.timerState

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UserPreferences(hasCompletedTutorial = true)
    )

    // Dispatches a toggle or manual-continue command to the foreground service.
    fun onPrimaryAction() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_TOGGLE_OR_CONTINUE)
    }

    // Skips to the next phase in Standard mode or swaps Work/Break in Flexible mode.
    fun onSkipPhase() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_SKIP_PHASE)
    }

    // Explicitly swaps between Work and Break while preserving remaining pool counts in Flexible mode.
    fun onSwapFlexiblePhase() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_SWAP_FLEXIBLE_PHASE)
    }

    // Resets the current preset timers to their full configured durations.
    fun onResetPhase() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_RESET_PHASE)
    }

    // Synchronously refreshes the timer state from SystemClock.elapsedRealtime() during Wear OS
    // Ambient Mode transitions and 1-minute onUpdateAmbient() wakeups.
    fun onAmbientUpdate() {
        TimerForegroundService.refreshRemainingTimeFromClock()
        if (timerState.value.isRunning) {
            sendServiceAction(TimerForegroundService.ACTION_AMBIENT_UPDATE)
        }
    }

    fun setRoutineMode(mode: RoutineMode) {
        viewModelScope.launch {
            preferencesRepository.setRoutineMode(mode)
        }
    }

    fun selectPreset(mode: RoutineMode, presetId: String) {
        viewModelScope.launch {
            preferencesRepository.selectPreset(mode, presetId)
        }
    }

    fun saveOrUpdatePreset(preset: TimerPreset) {
        viewModelScope.launch {
            preferencesRepository.saveOrUpdatePreset(preset, makeActive = true)
        }
    }

    fun deletePreset(mode: RoutineMode, presetId: String) {
        viewModelScope.launch {
            preferencesRepository.deletePreset(mode, presetId)
        }
    }

    fun setBackgroundMode(mode: BackgroundMode) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundMode(mode)
        }
    }

    fun setTouchWaveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTouchWaveEnabled(enabled)
        }
    }

    fun setOngoingPillEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOngoingPillEnabled(enabled)
        }
    }

    fun setReadinessGraceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReadinessGraceEnabled(enabled)
        }
    }

    fun setReadinessGraceSeconds(totalSeconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setReadinessGraceSeconds(totalSeconds.coerceAtLeast(1))
        }
    }

    fun setFontChoice(fontChoice: FontChoice) {
        viewModelScope.launch {
            preferencesRepository.setFontChoice(fontChoice)
        }
    }

    fun setAlertMode(alertMode: AlertMode) {
        viewModelScope.launch {
            preferencesRepository.setAlertMode(alertMode)
        }
    }

    fun setSoundOption(soundOption: SoundOption) {
        viewModelScope.launch {
            preferencesRepository.setSoundOption(soundOption)
            alertController.triggerPreview(preferences.value.copy(soundOption = soundOption))
        }
    }

    fun setAlertVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        viewModelScope.launch {
            preferencesRepository.setAlertVolume(clamped)
        }
    }

    fun setHasCompletedTutorial(completed: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHasCompletedTutorial(completed)
        }
    }

    fun previewAlert() {
        alertController.triggerPreview(preferences.value)
    }

    fun stopPreviewSound() {
        alertController.stopActiveSound()
    }

    override fun onCleared() {
        alertController.stopActiveSound()
        super.onCleared()
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(appContext, TimerForegroundService::class.java).apply {
            this.action = action
        }
        appContext.startForegroundService(intent)
    }
}
