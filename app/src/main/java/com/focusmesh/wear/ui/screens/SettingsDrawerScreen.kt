package com.focusmesh.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.AlertMode
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.UserPreferences
import kotlin.math.roundToInt

// Configuration drawer revealed by swiping from the left edge of the main timer screen.
// Supports rotary bezel/crown scrolling via ScalingLazyColumn on Galaxy Watch Ultra.
@Composable
fun SettingsDrawerScreen(
    preferences: UserPreferences,
    onToggleMesh3d: (Boolean) -> Unit,
    onToggleTouchWave: (Boolean) -> Unit,
    onToggleReadinessGrace: (Boolean) -> Unit,
    onCycleGraceMinutes: () -> Unit,
    onCycleWorkMinutes: () -> Unit,
    onCycleBreakMinutes: () -> Unit,
    onCycleFont: () -> Unit,
    onCycleAlertMode: () -> Unit,
    onCycleSoundOption: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviewAlert: () -> Unit,
    onSkipPhase: () -> Unit,
    onResetPhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    val alertModeLabel = when (preferences.alertMode) {
        AlertMode.SOUND_AND_HAPTICS -> stringResource(R.string.alert_mode_all)
        AlertMode.SOUND_ONLY -> stringResource(R.string.alert_mode_sound)
        AlertMode.HAPTICS_ONLY -> stringResource(R.string.alert_mode_haptics)
        AlertMode.NONE -> stringResource(R.string.alert_mode_none)
    }

    val soundOptionLabel = when (preferences.soundOption) {
        SoundOption.SINGING_BOWL -> stringResource(R.string.sound_singing_bowl)
        SoundOption.WARM_MARIMBA -> stringResource(R.string.sound_warm_marimba)
        SoundOption.CRYSTAL_CHIME -> stringResource(R.string.sound_crystal_chime)
        SoundOption.SOFT_GONG -> stringResource(R.string.sound_soft_gong)
        SoundOption.SYSTEM_ALARM -> stringResource(R.string.sound_system_alarm)
    }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            // 3D vs 2D Mesh background toggle.
            item {
                ToggleChip(
                    checked = preferences.mesh3dEnabled,
                    onCheckedChange = onToggleMesh3d,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_mesh_3d),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.settings_mesh_2d_sub),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = { Switch(checked = preferences.mesh3dEnabled) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Touch wave sheet physics toggle.
            item {
                ToggleChip(
                    checked = preferences.touchWaveEnabled,
                    onCheckedChange = onToggleTouchWave,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_touch_wave),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = { Switch(checked = preferences.touchWaveEnabled) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Off-by-default Readiness grace countdown toggle.
            item {
                ToggleChip(
                    checked = preferences.readinessGraceEnabled,
                    onCheckedChange = onToggleReadinessGrace,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_readiness_grace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = { Switch(checked = preferences.readinessGraceEnabled) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (preferences.readinessGraceEnabled) {
                item {
                    Chip(
                        onClick = onCycleGraceMinutes,
                        label = {
                            Text(
                                text = stringResource(
                                    R.string.settings_grace_minutes,
                                    preferences.readinessGraceMinutes
                                )
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Google Font family picker.
            item {
                Chip(
                    onClick = onCycleFont,
                    label = { Text(text = stringResource(R.string.settings_font_label)) },
                    secondaryLabel = { Text(text = preferences.fontChoice.displayName) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Alert mode selector (Sound + Haptics default, Sound Only, Haptics Only, Silent).
            item {
                Chip(
                    onClick = onCycleAlertMode,
                    label = { Text(text = stringResource(R.string.settings_alert_mode)) },
                    secondaryLabel = { Text(text = alertModeLabel) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Non-robotic acoustic sound preset selector.
            item {
                Chip(
                    onClick = onCycleSoundOption,
                    label = { Text(text = stringResource(R.string.settings_sound_option)) },
                    secondaryLabel = { Text(text = soundOptionLabel) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Independent alarm volume slider.
            item {
                Text(
                    text = stringResource(
                        R.string.settings_volume,
                        (preferences.alertVolume * 100f).roundToInt()
                    ),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                InlineSlider(
                    value = preferences.alertVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.0f..1.0f,
                    steps = 9,
                    decreaseIcon = { Text(text = "-", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    increaseIcon = { Text(text = "+", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Preview sound and haptics button.
            item {
                Chip(
                    onClick = onPreviewAlert,
                    label = { Text(text = stringResource(R.string.settings_test_alert)) },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF1F6FEB)),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Configurable Work and Break durations.
            item {
                Chip(
                    onClick = onCycleWorkMinutes,
                    label = {
                        Text(
                            text = stringResource(
                                R.string.settings_work_minutes,
                                preferences.workDurationMinutes
                            )
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = onCycleBreakMinutes,
                    label = {
                        Text(
                            text = stringResource(
                                R.string.settings_break_minutes,
                                preferences.breakDurationMinutes
                            )
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Phase control shortcuts.
            item {
                Chip(
                    onClick = onSkipPhase,
                    label = { Text(text = stringResource(R.string.settings_skip_phase)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = onResetPhase,
                    label = { Text(text = stringResource(R.string.settings_reset_timer)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
