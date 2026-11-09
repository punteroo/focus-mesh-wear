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
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property providing a singleton DataStore instance scoped to the application context.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_mesh_prefs")

// Repository managing asynchronous reads and atomic updates of user configuration on the watch.
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val WORK_SECONDS = intPreferencesKey("work_seconds")
        val BREAK_SECONDS = intPreferencesKey("break_seconds")
        val READINESS_GRACE_ENABLED = booleanPreferencesKey("readiness_grace_enabled")
        val READINESS_GRACE_SECONDS = intPreferencesKey("readiness_grace_seconds")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val TOUCH_WAVE_ENABLED = booleanPreferencesKey("touch_wave_enabled")
        val FONT_CHOICE = stringPreferencesKey("font_choice")
        val ALERT_MODE = stringPreferencesKey("alert_mode")
        val SOUND_OPTION = stringPreferencesKey("sound_option")
        val ALERT_VOLUME = floatPreferencesKey("alert_volume")
    }

    // Reactive stream emitting the latest UserPreferences whenever any setting changes.
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            workDurationSeconds = (prefs[Keys.WORK_SECONDS] ?: 3600).coerceIn(1, 86399),
            breakDurationSeconds = (prefs[Keys.BREAK_SECONDS] ?: 900).coerceIn(1, 86399),
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
            alertVolume = (prefs[Keys.ALERT_VOLUME] ?: 0.85f).coerceIn(0.0f, 1.0f)
        )
    }

    suspend fun setWorkDurationSeconds(totalSeconds: Int) {
        context.dataStore.edit { it[Keys.WORK_SECONDS] = totalSeconds.coerceIn(1, 86399) }
    }

    suspend fun setBreakDurationSeconds(totalSeconds: Int) {
        context.dataStore.edit { it[Keys.BREAK_SECONDS] = totalSeconds.coerceIn(1, 86399) }
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
}
