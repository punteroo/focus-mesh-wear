package com.focusmesh.wear.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Coordinates audible and tactile alerts when timer phases transition.
// Uses USAGE_ALARM for both audio and haptics so alerts bypass Do Not Disturb and Samsung Modes.
class AlertController(private val context: Context) {

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fires the configured sound and haptic pattern for the newly entered phase.
    fun triggerPhaseAlert(newPhase: TimerPhase, preferences: UserPreferences) {
        if (preferences.alertMode.emitsHaptics) {
            vibrateForPhase(newPhase)
        }
        if (preferences.alertMode.emitsSound && preferences.alertVolume > 0.01f) {
            alertScope.launch {
                playAlertSound(preferences.soundOption, preferences.alertVolume)
            }
        }
    }

    // Plays a short preview of the currently selected sound and haptic pattern from the settings drawer.
    fun triggerPreview(preferences: UserPreferences) {
        triggerPhaseAlert(TimerPhase.BREAK, preferences)
    }

    private fun playAlertSound(soundOption: SoundOption, volume: Float) {
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
                    ringtone.audioAttributes = alarmAudioAttributes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.volume = volume.coerceIn(0.0f, 1.0f)
                    }
                    ringtone.play()
                    return
                }
            }
        }

        // Play the multi-harmonic acoustic preset through a static AudioTrack on the alarm stream.
        val pcmSamples = AcousticsGenerator.generatePresetPcm(soundOption)
        val byteCount = pcmSamples.size * 2
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

        track.write(pcmSamples, 0, pcmSamples.size)
        track.setVolume(volume.coerceIn(0.0f, 1.0f))
        track.play()
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
