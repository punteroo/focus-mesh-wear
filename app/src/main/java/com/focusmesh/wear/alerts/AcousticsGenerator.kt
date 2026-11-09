package com.focusmesh.wear.alerts

import com.focusmesh.wear.domain.model.SoundOption
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// Synthesizes warm, organic 16-bit PCM audio buffers using multi-harmonic additive partials.
// This avoids harsh square-wave or robotic buzzer tones while requiring zero external binary assets.
object AcousticsGenerator {

    const val SAMPLE_RATE = 44100

    // Generates a mono 16-bit PCM buffer for the requested acoustic preset.
    fun generatePresetPcm(option: SoundOption): ShortArray = when (option) {
        SoundOption.SINGING_BOWL -> synthesizeSingingBowl()
        SoundOption.WARM_MARIMBA -> synthesizeWarmMarimba()
        SoundOption.CRYSTAL_CHIME -> synthesizeCrystalChime()
        SoundOption.SOFT_GONG -> synthesizeSoftGong()
        SoundOption.SYSTEM_ALARM -> synthesizeSingingBowl()
    }

    // Tibetan singing bowl with a 432 Hz fundamental, warm beating overtones, and a 2.5 second resonant tail.
    private fun synthesizeSingingBowl(): ShortArray {
        val durationSeconds = 2.5
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.035).coerceAtMost(1.0)
            val fundamental = sin(2.0 * PI * 432.0 * t) * exp(-t * 1.35)
            val beatingHarmonic = sin(2.0 * PI * 434.2 * t) * 0.45 * exp(-t * 1.45)
            val fifthOvertone = sin(2.0 * PI * 648.0 * t) * 0.30 * exp(-t * 2.10)
            val shimmerOvertone = sin(2.0 * PI * 1188.0 * t) * 0.14 * exp(-t * 3.40)
            val sample = attack * (fundamental + beatingHarmonic + fifthOvertone + shimmerOvertone) * 0.48
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    // Two-note ascending rosewood marimba interval (C5 followed by G5) with woody fourth-harmonic resonance.
    private fun synthesizeWarmMarimba(): ShortArray {
        val durationSeconds = 1.6
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        val secondNoteStart = 0.28
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var signal = renderMarimbaBar(t, 523.25)
            if (t >= secondNoteStart) {
                signal += renderMarimbaBar(t - secondNoteStart, 783.99)
            }
            val sample = (signal * 0.55).coerceIn(-1.0, 1.0)
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    private fun renderMarimbaBar(localTime: Double, freq: Double): Double {
        val attack = (localTime / 0.008).coerceAtMost(1.0)
        val fundamental = sin(2.0 * PI * freq * localTime) * exp(-localTime * 5.5)
        val fourthHarmonic = sin(2.0 * PI * (freq * 4.0) * localTime) * 0.28 * exp(-localTime * 16.0)
        return attack * (fundamental + fourthHarmonic)
    }

    // Three-note pentatonic wind chime arpeggio with clear bell harmonics.
    private fun synthesizeCrystalChime(): ShortArray {
        val durationSeconds = 2.2
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        val notes = listOf(0.0 to 587.33, 0.22 to 739.99, 0.44 to 880.00)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var signal = 0.0
            for ((offset, freq) in notes) {
                if (t >= offset) {
                    val lt = t - offset
                    val attack = (lt / 0.012).coerceAtMost(1.0)
                    val tone = sin(2.0 * PI * freq * lt) * exp(-lt * 2.4) +
                        sin(2.0 * PI * (freq * 2.756) * lt) * 0.22 * exp(-lt * 4.8)
                    signal += attack * tone
                }
            }
            val sample = (signal * 0.36).coerceIn(-1.0, 1.0)
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    // Low resonant zen gong at 220 Hz with a warm sub-octave bloom.
    private fun synthesizeSoftGong(): ShortArray {
        val durationSeconds = 2.6
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.05).coerceAtMost(1.0)
            val lowFundamental = sin(2.0 * PI * 220.0 * t) * exp(-t * 1.25)
            val warmOctave = sin(2.0 * PI * 440.0 * t) * 0.40 * exp(-t * 1.65)
            val fifthPartial = sin(2.0 * PI * 330.0 * t) * 0.32 * exp(-t * 2.00)
            val metallicBloom = sin(2.0 * PI * 554.37 * t) * (1.0 - exp(-t * 4.0)) * 0.18 * exp(-t * 2.3)
            val sample = attack * (lowFundamental + warmOctave + fifthPartial + metallicBloom) * 0.45
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }
}
