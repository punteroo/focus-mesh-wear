package com.focusmesh.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusmesh.wear.domain.model.AlertMode
import com.focusmesh.wear.domain.model.BackgroundMode
import com.focusmesh.wear.domain.model.FontChoice
import com.focusmesh.wear.domain.model.RoutineMode
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.TimerPreset
import com.focusmesh.wear.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property providing a singleton DataStore instance scoped to the application context.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_mesh_prefs")

// Repository managing asynchronous reads and atomic updates of user configuration, per-mode presets,
// and onboarding tutorial state on the watch.
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val ROUTINE_MODE = stringPreferencesKey("routine_mode")
        val STANDARD_PRESETS = stringPreferencesKey("standard_presets_v1")
        val FLEXIBLE_PRESETS = stringPreferencesKey("flexible_presets_v1")
        val ACTIVE_STANDARD_PRESET_ID = stringPreferencesKey("active_standard_preset_id")
        val ACTIVE_FLEXIBLE_PRESET_ID = stringPreferencesKey("active_flexible_preset_id")
        val READINESS_GRACE_ENABLED = booleanPreferencesKey("readiness_grace_enabled")
        val READINESS_GRACE_SECONDS = intPreferencesKey("readiness_grace_seconds")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val TOUCH_WAVE_ENABLED = booleanPreferencesKey("touch_wave_enabled")
        val FONT_CHOICE = stringPreferencesKey("font_choice")
        val ALERT_MODE = stringPreferencesKey("alert_mode")
        val SOUND_OPTION = stringPreferencesKey("sound_option")
        val ALERT_VOLUME = floatPreferencesKey("alert_volume")
        val ONGOING_PILL_ENABLED = booleanPreferencesKey("ongoing_pill_enabled")
        val HAS_COMPLETED_TUTORIAL = booleanPreferencesKey("has_completed_tutorial")
    }

    // Reactive stream emitting the latest UserPreferences whenever any setting or preset changes.
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val routineMode = runCatching {
            RoutineMode.valueOf(prefs[Keys.ROUTINE_MODE] ?: RoutineMode.STANDARD.name)
        }.getOrDefault(RoutineMode.STANDARD)

        val standardPresets = decodePresets(
            encoded = prefs[Keys.STANDARD_PRESETS],
            mode = RoutineMode.STANDARD,
            defaultPreset = TimerPreset.defaultStandardPreset()
        )
        val flexiblePresets = decodePresets(
            encoded = prefs[Keys.FLEXIBLE_PRESETS],
            mode = RoutineMode.FLEXIBLE,
            defaultPreset = TimerPreset.defaultFlexiblePreset()
        )

        val activeStdId = prefs[Keys.ACTIVE_STANDARD_PRESET_ID]
            ?.takeIf { id -> standardPresets.any { it.id == id } }
            ?: standardPresets.first().id

        val activeFlexId = prefs[Keys.ACTIVE_FLEXIBLE_PRESET_ID]
            ?.takeIf { id -> flexiblePresets.any { it.id == id } }
            ?: flexiblePresets.first().id

        UserPreferences(
            routineMode = routineMode,
            standardPresets = standardPresets,
            flexiblePresets = flexiblePresets,
            activeStandardPresetId = activeStdId,
            activeFlexiblePresetId = activeFlexId,
            readinessGraceEnabled = prefs[Keys.READINESS_GRACE_ENABLED] ?: false,
            readinessGraceSeconds = (prefs[Keys.READINESS_GRACE_SECONDS] ?: 300).coerceIn(1, 86399),
            backgroundMode = runCatching {
                BackgroundMode.valueOf(prefs[Keys.BACKGROUND_MODE] ?: BackgroundMode.MESH_3D.name)
            }.getOrDefault(BackgroundMode.MESH_3D),
            touchWaveEnabled = prefs[Keys.TOUCH_WAVE_ENABLED] ?: true,
            fontChoice = runCatching {
                FontChoice.valueOf(prefs[Keys.FONT_CHOICE] ?: FontChoice.SPACE_GROTESK.name)
            }.getOrDefault(FontChoice.SPACE_GROTESK),
            alertMode = runCatching {
                AlertMode.valueOf(prefs[Keys.ALERT_MODE] ?: AlertMode.SOUND_AND_HAPTICS.name)
            }.getOrDefault(AlertMode.SOUND_AND_HAPTICS),
            soundOption = runCatching {
                SoundOption.valueOf(prefs[Keys.SOUND_OPTION] ?: SoundOption.SINGING_BOWL.name)
            }.getOrDefault(SoundOption.SINGING_BOWL),
            alertVolume = (prefs[Keys.ALERT_VOLUME] ?: 0.85f).coerceIn(0.0f, 1.0f),
            ongoingPillEnabled = prefs[Keys.ONGOING_PILL_ENABLED] ?: true,
            hasCompletedTutorial = prefs[Keys.HAS_COMPLETED_TUTORIAL] ?: false
        )
    }

    suspend fun setRoutineMode(mode: RoutineMode) {
        context.dataStore.edit { it[Keys.ROUTINE_MODE] = mode.name }
    }

    suspend fun selectPreset(mode: RoutineMode, presetId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROUTINE_MODE] = mode.name
            when (mode) {
                RoutineMode.STANDARD -> prefs[Keys.ACTIVE_STANDARD_PRESET_ID] = presetId
                RoutineMode.FLEXIBLE -> prefs[Keys.ACTIVE_FLEXIBLE_PRESET_ID] = presetId
            }
        }
    }

    suspend fun saveOrUpdatePreset(preset: TimerPreset, makeActive: Boolean = true) {
        val sanitized = preset.copy(
            name = TimerPreset.sanitizeName(preset.name),
            workDurationSeconds = preset.workDurationSeconds.coerceIn(1, 86399),
            breakDurationSeconds = preset.breakDurationSeconds.coerceIn(1, 86399)
        )
        context.dataStore.edit { prefs ->
            val key = if (sanitized.mode == RoutineMode.STANDARD) {
                Keys.STANDARD_PRESETS
            } else {
                Keys.FLEXIBLE_PRESETS
            }
            val defaultPreset = if (sanitized.mode == RoutineMode.STANDARD) {
                TimerPreset.defaultStandardPreset()
            } else {
                TimerPreset.defaultFlexiblePreset()
            }
            val currentList = decodePresets(prefs[key], sanitized.mode, defaultPreset).toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == sanitized.id }
            if (existingIndex >= 0) {
                val preserveDefaultFlag = currentList[existingIndex].isDefault
                currentList[existingIndex] = sanitized.copy(isDefault = preserveDefaultFlag)
            } else {
                currentList.add(sanitized.copy(isDefault = false))
            }
            prefs[key] = encodePresets(currentList)

            if (makeActive) {
                if (sanitized.mode == RoutineMode.STANDARD) {
                    prefs[Keys.ACTIVE_STANDARD_PRESET_ID] = sanitized.id
                } else {
                    prefs[Keys.ACTIVE_FLEXIBLE_PRESET_ID] = sanitized.id
                }
            }
        }
    }

    // Deletes a user-created preset while strictly preserving non-deletable default presets.
    suspend fun deletePreset(mode: RoutineMode, presetId: String) {
        context.dataStore.edit { prefs ->
            val key = if (mode == RoutineMode.STANDARD) Keys.STANDARD_PRESETS else Keys.FLEXIBLE_PRESETS
            val defaultPreset = if (mode == RoutineMode.STANDARD) {
                TimerPreset.defaultStandardPreset()
            } else {
                TimerPreset.defaultFlexiblePreset()
            }
            val currentList = decodePresets(prefs[key], mode, defaultPreset)
            val updatedList = currentList.filterNot { !it.isDefault && it.id == presetId }
                .ifEmpty { listOf(defaultPreset) }
            prefs[key] = encodePresets(updatedList)

            val activeKey = if (mode == RoutineMode.STANDARD) {
                Keys.ACTIVE_STANDARD_PRESET_ID
            } else {
                Keys.ACTIVE_FLEXIBLE_PRESET_ID
            }
            if (prefs[activeKey] == presetId) {
                prefs[activeKey] = updatedList.first().id
            }
        }
    }

    suspend fun setReadinessGraceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READINESS_GRACE_ENABLED] = enabled }
    }

    suspend fun setReadinessGraceSeconds(totalSeconds: Int) {
        context.dataStore.edit { it[Keys.READINESS_GRACE_SECONDS] = totalSeconds.coerceIn(1, 86399) }
    }

    suspend fun setBackgroundMode(mode: BackgroundMode) {
        context.dataStore.edit { it[Keys.BACKGROUND_MODE] = mode.name }
    }

    suspend fun setTouchWaveEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TOUCH_WAVE_ENABLED] = enabled }
    }

    suspend fun setFontChoice(fontChoice: FontChoice) {
        context.dataStore.edit { it[Keys.FONT_CHOICE] = fontChoice.name }
    }

    suspend fun setAlertMode(alertMode: AlertMode) {
        context.dataStore.edit { it[Keys.ALERT_MODE] = alertMode.name }
    }

    suspend fun setSoundOption(soundOption: SoundOption) {
        context.dataStore.edit { it[Keys.SOUND_OPTION] = soundOption.name }
    }

    suspend fun setAlertVolume(volume: Float) {
        context.dataStore.edit { it[Keys.ALERT_VOLUME] = volume.coerceIn(0.0f, 1.0f) }
    }

    suspend fun setOngoingPillEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ONGOING_PILL_ENABLED] = enabled }
    }

    suspend fun setHasCompletedTutorial(completed: Boolean) {
        context.dataStore.edit { it[Keys.HAS_COMPLETED_TUTORIAL] = completed }
    }

    private fun encodePresets(presets: List<TimerPreset>): String {
        return presets.joinToString(separator = "\n") { preset ->
            val safeName = TimerPreset.sanitizeName(preset.name)
            "${preset.id}|$safeName|${preset.workDurationSeconds}|${preset.breakDurationSeconds}|${preset.isDefault}"
        }
    }

    private fun decodePresets(
        encoded: String?,
        mode: RoutineMode,
        defaultPreset: TimerPreset
    ): List<TimerPreset> {
        if (encoded.isNullOrBlank()) return listOf(defaultPreset)
        val parsed = encoded.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 5) return@mapNotNull null
            val id = parts[0].trim().ifEmpty { return@mapNotNull null }
            val name = TimerPreset.sanitizeName(parts[1])
            val workSec = parts[2].toIntOrNull()?.coerceIn(1, 86399) ?: defaultPreset.workDurationSeconds
            val breakSec = parts[3].toIntOrNull()?.coerceIn(1, 86399) ?: defaultPreset.breakDurationSeconds
            val isDefault = parts[4].toBooleanStrictOrNull() ?: (id == defaultPreset.id)
            TimerPreset(
                id = id,
                mode = mode,
                name = name,
                workDurationSeconds = workSec,
                breakDurationSeconds = breakSec,
                isDefault = isDefault
            )
        }
        if (parsed.isEmpty()) return listOf(defaultPreset)
        return if (parsed.any { it.isDefault }) {
            parsed
        } else {
            listOf(defaultPreset) + parsed
        }
    }
}
