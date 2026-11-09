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

    fun setMesh3dEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMesh3dEnabled(enabled)
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

    fun cycleReadinessGraceMinutes() {
        val current = preferences.value.readinessGraceMinutes
        val next = when (current) {
            2 -> 5
            5 -> 10
            10 -> 15
            else -> 2
        }
        viewModelScope.launch {
            preferencesRepository.setReadinessGraceMinutes(next)
        }
    }

    fun cycleWorkDurationMinutes() {
        val current = preferences.value.workDurationMinutes
        val next = when (current) {
            45 -> 60
            60 -> 75
            75 -> 90
            90 -> 30
            else -> 60
        }
        viewModelScope.launch {
            preferencesRepository.setWorkDurationMinutes(next)
        }
    }

    fun cycleBreakDurationMinutes() {
        val current = preferences.value.breakDurationMinutes
        val next = when (current) {
            10 -> 15
            15 -> 20
            20 -> 5
            else -> 15
        }
        viewModelScope.launch {
            preferencesRepository.setBreakDurationMinutes(next)
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
