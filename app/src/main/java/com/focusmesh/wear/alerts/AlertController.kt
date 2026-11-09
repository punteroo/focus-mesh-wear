package com.focusmesh.wear.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Coordinates audible and tactile alerts when timer phases transition.
// Uses USAGE_ALARM for both audio and haptics so alerts bypass Do Not Disturb and Samsung Modes.
// Tracks active Ringtone and AudioTrack instances to guarantee single-shot playback with no infinite loops.
class AlertController(private val context: Context) {

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var activeSoundJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var activeAudioTrack: AudioTrack? = null

    // Fires the configured sound and haptic pattern for the newly entered phase.
    fun triggerPhaseAlert(newPhase: TimerPhase, preferences: UserPreferences) {
        if (preferences.alertMode.emitsHaptics) {
            vibrateForPhase(newPhase)
        }
        if (preferences.alertMode.emitsSound && preferences.alertVolume > 0.01f) {
            startSingleShotSound(preferences.soundOption, preferences.alertVolume)
        } else {
            stopActiveSound()
        }
    }

    // Plays a short single-shot preview of the selected sound and haptic pattern from the settings drawer.
    // Calling this while a preview is already playing stops the current sound and starts a fresh preview.
    fun triggerPreview(preferences: UserPreferences) {
        stopActiveSound()
        if (preferences.alertMode.emitsHaptics) {
            vibrateForPhase(TimerPhase.BREAK)
        }
        if (preferences.alertMode.emitsSound && preferences.alertVolume > 0.01f) {
            startSingleShotSound(preferences.soundOption, preferences.alertVolume)
        }
    }

    // Stops any currently playing ringtone or synthesized audio track immediately.
    fun stopActiveSound() {
        synchronized(lock) {
            activeSoundJob?.cancel()
            activeSoundJob = null
            stopResourcesLocked()
        }
    }

    private fun startSingleShotSound(soundOption: SoundOption, volume: Float) {
        synchronized(lock) {
            activeSoundJob?.cancel()
            stopResourcesLocked()
            activeSoundJob = alertScope.launch {
                playAlertSoundOnce(soundOption, volume)
            }
        }
    }

    private fun stopResourcesLocked() {
        runCatching {
            activeRingtone?.stop()
        }
        activeRingtone = null

        runCatching {
            activeAudioTrack?.pause()
            activeAudioTrack?.flush()
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        }
        activeAudioTrack = null
    }

    private suspend fun playAlertSoundOnce(soundOption: SoundOption, volume: Float) {
        val alarmAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (soundOption == SoundOption.SYSTEM_ALARM) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                if (ringtone != null) {
                    try {
                        ringtone.audioAttributes = alarmAudioAttributes
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // System alarm tones default to looping on Wear OS; disable looping explicitly.
                            ringtone.isLooping = false
                            ringtone.volume = volume.coerceIn(0.0f, 1.0f)
                        }
                        synchronized(lock) {
                            activeRingtone = ringtone
                        }
                        ringtone.play()
                        // Enforce a hard 2.2 second cutoff so system alarm tones never loop indefinitely.
                        delay(2200L)
                    } finally {
                        runCatching { ringtone.stop() }
                        synchronized(lock) {
                            if (activeRingtone === ringtone) {
                                activeRingtone = null
                            }
                        }
                    }
                    return
                }
            }
        }

        // Play the multi-harmonic acoustic preset through a static AudioTrack and release it once complete.
        val pcmSamples = AcousticsGenerator.generatePresetPcm(soundOption)
        val byteCount = pcmSamples.size * 2
        val durationMillis = (pcmSamples.size * 1000L) / AcousticsGenerator.SAMPLE_RATE

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AcousticsGenerator.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(alarmAudioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(byteCount)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcmSamples, 0, pcmSamples.size)
            track.setVolume(volume.coerceIn(0.0f, 1.0f))
            synchronized(lock) {
                activeAudioTrack = track
            }
            track.play()
            delay(durationMillis + 150L)
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
            synchronized(lock) {
                if (activeAudioTrack === track) {
                    activeAudioTrack = null
                }
            }
        }
    }

    private fun vibrateForPhase(newPhase: TimerPhase) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        // Distinct tactile signatures so the user knows which phase started without looking at the watch.
        val effect = when (newPhase) {
            TimerPhase.BREAK -> VibrationEffect.createWaveform(
                longArrayOf(0L, 180L, 140L, 260L, 180L, 400L),
                intArrayOf(0, 220, 0, 180, 0, 130),
                -1
            )
            TimerPhase.READINESS -> VibrationEffect.createWaveform(
                longArrayOf(0L, 90L, 70L, 120L, 200L, 90L, 70L, 150L),
                intArrayOf(0, 200, 0, 255, 0, 200, 0, 255),
                -1
            )
            TimerPhase.WORK -> VibrationEffect.createWaveform(
                longArrayOf(0L, 140L, 100L, 140L),
                intArrayOf(0, 180, 0, 240),
                -1
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val vibrationAttributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, vibrationAttributes)
        } else {
            @Suppress("DEPRECATION")
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
        }
    }
}
