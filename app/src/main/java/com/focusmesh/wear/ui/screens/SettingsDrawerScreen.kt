package com.focusmesh.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.rememberPickerState
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.AlertMode
import com.focusmesh.wear.domain.model.BackgroundMode
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.ui.theme.resolveGoogleFontFamily
import kotlin.math.roundToInt

private enum class ActiveDurationTarget {
    WORK,
    BREAK,
    GRACE
}

// Configuration drawer revealed by swiping left from the right edge of the watch screen.
// Includes a 3-column Hour : Minute : Second custom duration picker, live font preview, and 3-way background selector.
@Composable
fun SettingsDrawerScreen(
    preferences: UserPreferences,
    isBatterySaverActive: Boolean,
    onCycleBackgroundMode: () -> Unit,
    onToggleTouchWave: (Boolean) -> Unit,
    onToggleReadinessGrace: (Boolean) -> Unit,
    onSaveWorkDurationSeconds: (Int) -> Unit,
    onSaveBreakDurationSeconds: (Int) -> Unit,
    onSaveGraceDurationSeconds: (Int) -> Unit,
    onCycleFont: () -> Unit,
    onCycleAlertMode: () -> Unit,
    onCycleSoundOption: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviewAlert: () -> Unit,
    onSkipPhase: () -> Unit,
    onResetPhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activePickerTarget by remember { mutableStateOf<ActiveDurationTarget?>(null) }

    // If the user tapped a duration item, render the full-screen Hour : Minute : Second drum picker.
    val currentTarget = activePickerTarget
    if (currentTarget != null) {
        val initialSeconds = when (currentTarget) {
            ActiveDurationTarget.WORK -> preferences.workDurationSeconds
            ActiveDurationTarget.BREAK -> preferences.breakDurationSeconds
            ActiveDurationTarget.GRACE -> preferences.readinessGraceSeconds
        }
        val pickerTitle = when (currentTarget) {
            ActiveDurationTarget.WORK -> stringResource(R.string.settings_work_duration)
            ActiveDurationTarget.BREAK -> stringResource(R.string.settings_break_duration)
            ActiveDurationTarget.GRACE -> stringResource(R.string.settings_grace_duration)
        }
        HmsDurationPickerOverlay(
            title = pickerTitle,
            initialTotalSeconds = initialSeconds,
            onConfirmSeconds = { updatedSeconds ->
                when (currentTarget) {
                    ActiveDurationTarget.WORK -> onSaveWorkDurationSeconds(updatedSeconds)
                    ActiveDurationTarget.BREAK -> onSaveBreakDurationSeconds(updatedSeconds)
                    ActiveDurationTarget.GRACE -> onSaveGraceDurationSeconds(updatedSeconds)
                }
                activePickerTarget = null
            },
            onCancel = { activePickerTarget = null }
        )
        return
    }

    val listState = rememberScalingLazyListState()
    val previewFontFamily = remember(preferences.fontChoice) {
        resolveGoogleFontFamily(preferences.fontChoice)
    }

    val bgModeLabel = when (preferences.backgroundMode) {
        BackgroundMode.MESH_3D -> stringResource(R.string.bg_mode_3d)
        BackgroundMode.MESH_2D -> stringResource(R.string.bg_mode_2d)
        BackgroundMode.SOLID_COLOR -> stringResource(R.string.bg_mode_solid)
    }

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

            // Custom Work Duration (Hour : Minute : Second granularity).
            item {
                Chip(
                    onClick = { activePickerTarget = ActiveDurationTarget.WORK },
                    label = { Text(text = stringResource(R.string.settings_work_duration)) },
                    secondaryLabel = {
                        Text(text = UserPreferences.formatSecondsAsHms(preferences.workDurationSeconds))
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Custom Break Duration (Hour : Minute : Second granularity).
            item {
                Chip(
                    onClick = { activePickerTarget = ActiveDurationTarget.BREAK },
                    label = { Text(text = stringResource(R.string.settings_break_duration)) },
                    secondaryLabel = {
                        Text(text = UserPreferences.formatSecondsAsHms(preferences.breakDurationSeconds))
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Off-by-default Readiness grace countdown toggle and custom HH:MM:SS duration.
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
                        onClick = { activePickerTarget = ActiveDurationTarget.GRACE },
                        label = { Text(text = stringResource(R.string.settings_grace_duration)) },
                        secondaryLabel = {
                            Text(text = UserPreferences.formatSecondsAsHms(preferences.readinessGraceSeconds))
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3-way Background Mode selector (3D Mesh, 2D Mesh, Solid Color) with Battery Saver status.
            item {
                Chip(
                    onClick = onCycleBackgroundMode,
                    label = { Text(text = stringResource(R.string.settings_bg_mode)) },
                    secondaryLabel = {
                        Text(
                            text = if (isBatterySaverActive) {
                                stringResource(R.string.bg_battery_saver_active)
                            } else {
                                bgModeLabel
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (preferences.backgroundMode != BackgroundMode.SOLID_COLOR && !isBatterySaverActive) {
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
            }

            // Font Family selector with live inline typography preview.
            item {
                Chip(
                    onClick = onCycleFont,
                    label = {
                        Text(
                            text = "${stringResource(R.string.settings_font_label)}: ${preferences.fontChoice.displayName}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.settings_font_preview_sample),
                            style = TextStyle(
                                fontFamily = previewFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.0.sp,
                                color = Color(0xFFFF6D1A)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Alert mode selector.
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

// Full-screen Wear OS 3-column drum picker allowing the user to dial in any exact Hour, Minute, and Second.
@Composable
private fun HmsDurationPickerOverlay(
    title: String,
    initialTotalSeconds: Int,
    onConfirmSeconds: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val clamped = initialTotalSeconds.coerceIn(1, 86399)
    val initialHours = clamped / 3600
    val initialMinutes = (clamped % 3600) / 60
    val initialSeconds = clamped % 60

    val hoursState = rememberPickerState(
        initialNumberOfOptions = 24,
        initiallySelectedOption = initialHours
    )
    val minutesState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = initialMinutes
    )
    val secondsState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = initialSeconds
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Hours column (00 to 23).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.picker_hours),
                    fontSize = 9.sp,
                    color = Color(0xFFFF6D1A)
                )
                Picker(
                    state = hoursState,
                    contentDescription = stringResource(R.string.picker_hours),
                    modifier = Modifier.fillMaxSize()
                ) { hour ->
                    Text(
                        text = String.format("%02d", hour),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hour == hoursState.selectedOption) Color.White else Color.Gray
                    )
                }
            }

            Text(
                text = ":",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 10.dp)
            )

            // Minutes column (00 to 59).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.picker_minutes),
                    fontSize = 9.sp,
                    color = Color(0xFFFF6D1A)
                )
                Picker(
                    state = minutesState,
                    contentDescription = stringResource(R.string.picker_minutes),
                    modifier = Modifier.fillMaxSize()
                ) { minute ->
                    Text(
                        text = String.format("%02d", minute),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (minute == minutesState.selectedOption) Color.White else Color.Gray
                    )
                }
            }

            Text(
                text = ":",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 10.dp)
            )

            // Seconds column (00 to 59).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.picker_seconds),
                    fontSize = 9.sp,
                    color = Color(0xFFFF6D1A)
                )
                Picker(
                    state = secondsState,
                    contentDescription = stringResource(R.string.picker_seconds),
                    modifier = Modifier.fillMaxSize()
                ) { second ->
                    Text(
                        text = String.format("%02d", second),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (second == secondsState.selectedOption) Color.White else Color.Gray
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactChip(
                onClick = onCancel,
                label = { Text(text = stringResource(R.string.picker_cancel), fontSize = 11.sp) },
                colors = ChipDefaults.secondaryChipColors()
            )
            Spacer(modifier = Modifier.width(8.dp))
            CompactChip(
                onClick = {
                    val total = (hoursState.selectedOption * 3600) +
                        (minutesState.selectedOption * 60) +
                        secondsState.selectedOption
                    onConfirmSeconds(total.coerceAtLeast(1))
                },
                label = { Text(text = stringResource(R.string.picker_save), fontSize = 11.sp) },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFFFF6D1A))
            )
        }
    }
}
