package com.focusmesh.wear.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusmesh.wear.alerts.AlertController
import com.focusmesh.wear.data.PreferencesRepository
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
        initialValue = UserPreferences()
    )

    // Dispatches a toggle or manual-continue command to the foreground service.
    fun onPrimaryAction() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_TOGGLE_OR_CONTINUE)
    }

    // Skips immediately to the next phase in the Work -> Break -> Readiness loop.
    fun onSkipPhase() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_SKIP_PHASE)
    }

    // Resets the current phase timer to its full configured duration.
    fun onResetPhase() {
        alertController.stopActiveSound()
        sendServiceAction(TimerForegroundService.ACTION_RESET_PHASE)
    }

    fun cycleBackgroundMode() {
        val next = preferences.value.backgroundMode.next()
        viewModelScope.launch {
            preferencesRepository.setBackgroundMode(next)
        }
    }

    fun setTouchWaveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTouchWaveEnabled(enabled)
        }
    }

    fun setReadinessGraceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setReadinessGraceEnabled(enabled)
        }
    }

    fun setWorkDurationSeconds(totalSeconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setWorkDurationSeconds(totalSeconds.coerceAtLeast(1))
        }
    }

    fun setBreakDurationSeconds(totalSeconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setBreakDurationSeconds(totalSeconds.coerceAtLeast(1))
        }
    }

    fun setReadinessGraceSeconds(totalSeconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setReadinessGraceSeconds(totalSeconds.coerceAtLeast(1))
        }
    }

    fun cycleFontChoice() {
        val next = preferences.value.fontChoice.next()
        viewModelScope.launch {
            preferencesRepository.setFontChoice(next)
        }
    }

    fun cycleAlertMode() {
        val next = preferences.value.alertMode.next()
        viewModelScope.launch {
            preferencesRepository.setAlertMode(next)
        }
    }

    fun cycleSoundOption() {
        val next = preferences.value.soundOption.next()
        viewModelScope.launch {
            preferencesRepository.setSoundOption(next)
            alertController.triggerPreview(preferences.value.copy(soundOption = next))
        }
    }

    fun setAlertVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        viewModelScope.launch {
            preferencesRepository.setAlertVolume(clamped)
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
