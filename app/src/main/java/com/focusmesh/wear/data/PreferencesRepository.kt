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
        val WORK_MINUTES = intPreferencesKey("work_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val READINESS_GRACE_ENABLED = booleanPreferencesKey("readiness_grace_enabled")
        val READINESS_GRACE_MINUTES = intPreferencesKey("readiness_grace_minutes")
        val MESH_3D_ENABLED = booleanPreferencesKey("mesh_3d_enabled")
        val TOUCH_WAVE_ENABLED = booleanPreferencesKey("touch_wave_enabled")
        val FONT_CHOICE = stringPreferencesKey("font_choice")
        val ALERT_MODE = stringPreferencesKey("alert_mode")
        val SOUND_OPTION = stringPreferencesKey("sound_option")
        val ALERT_VOLUME = floatPreferencesKey("alert_volume")
    }

    // Reactive stream emitting the latest UserPreferences whenever any setting changes.
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            workDurationMinutes = prefs[Keys.WORK_MINUTES] ?: 60,
            breakDurationMinutes = prefs[Keys.BREAK_MINUTES] ?: 15,
            readinessGraceEnabled = prefs[Keys.READINESS_GRACE_ENABLED] ?: false,
            readinessGraceMinutes = prefs[Keys.READINESS_GRACE_MINUTES] ?: 5,
            mesh3dEnabled = prefs[Keys.MESH_3D_ENABLED] ?: true,
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

    suspend fun setWorkDurationMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.WORK_MINUTES] = minutes.coerceIn(5, 180) }
    }

    suspend fun setBreakDurationMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.BREAK_MINUTES] = minutes.coerceIn(1, 60) }
    }

    suspend fun setReadinessGraceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.READINESS_GRACE_ENABLED] = enabled }
    }

    suspend fun setReadinessGraceMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.READINESS_GRACE_MINUTES] = minutes.coerceIn(1, 30) }
    }

    suspend fun setMesh3dEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MESH_3D_ENABLED] = enabled }
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
