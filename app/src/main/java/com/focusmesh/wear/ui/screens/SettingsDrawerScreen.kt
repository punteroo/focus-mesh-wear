package com.focusmesh.wear.ui.screens

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.rememberPickerState
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.AlertMode
import com.focusmesh.wear.domain.model.BackgroundMode
import com.focusmesh.wear.domain.model.FontChoice
import com.focusmesh.wear.domain.model.RoutineMode
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.TimerPreset
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.ui.theme.resolveGoogleFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface SettingsSubScreen {
    data object ModeSelector : SettingsSubScreen
    data class ModePresetsList(val mode: RoutineMode) : SettingsSubScreen
    data class EditPreset(val preset: TimerPreset) : SettingsSubScreen
    data class EditPresetWorkDuration(val preset: TimerPreset) : SettingsSubScreen
    data class EditPresetBreakDuration(val preset: TimerPreset) : SettingsSubScreen
    data object GraceDuration : SettingsSubScreen
    data object BackgroundSelector : SettingsSubScreen
    data object FontSelector : SettingsSubScreen
    data object AlertModeSelector : SettingsSubScreen
    data object SoundSelector : SettingsSubScreen
}

// Native Wear OS configuration drawer using official Wear Compose Material primitives:
// ScalingLazyColumn with rotary bezel scroll, ListHeader, Chip, ToggleChip with Switch/RadioButton,
// InlineSlider with InlineSliderDefaults icons, per-mode Preset management, and native 3-column Picker.
@Composable
fun SettingsDrawerScreen(
    preferences: UserPreferences,
    isBatterySaverActive: Boolean,
    onSelectRoutineMode: (RoutineMode) -> Unit,
    onSelectPreset: (RoutineMode, String) -> Unit,
    onSaveOrUpdatePreset: (TimerPreset) -> Unit,
    onDeletePreset: (RoutineMode, String) -> Unit,
    onOpenTutorial: () -> Unit,
    onSelectBackgroundMode: (BackgroundMode) -> Unit,
    onToggleTouchWave: (Boolean) -> Unit,
    onToggleOngoingPill: (Boolean) -> Unit,
    onToggleReadinessGrace: (Boolean) -> Unit,
    onSaveGraceDurationSeconds: (Int) -> Unit,
    onSelectFontChoice: (FontChoice) -> Unit,
    onSelectAlertMode: (AlertMode) -> Unit,
    onSelectSoundOption: (SoundOption) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviewAlert: () -> Unit,
    onSkipPhase: () -> Unit,
    onResetPhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

    // Navigate back hierarchically through sub-screens using Wear OS Back gesture.
    BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = when (val current = activeSubScreen) {
            is SettingsSubScreen.EditPresetWorkDuration -> SettingsSubScreen.EditPreset(current.preset)
            is SettingsSubScreen.EditPresetBreakDuration -> SettingsSubScreen.EditPreset(current.preset)
            is SettingsSubScreen.EditPreset -> SettingsSubScreen.ModePresetsList(current.preset.mode)
            else -> null
        }
    }

    when (val sub = activeSubScreen) {
        SettingsSubScreen.ModeSelector -> {
            NativeRoutineModeSelectorScreen(
                selectedMode = preferences.routineMode,
                onSelectMode = {
                    onSelectRoutineMode(it)
                    activeSubScreen = null
                }
            )
            return
        }
        is SettingsSubScreen.ModePresetsList -> {
            NativeModePresetsListScreen(
                mode = sub.mode,
                preferences = preferences,
                onOpenPresetEditor = { preset ->
                    activeSubScreen = SettingsSubScreen.EditPreset(preset)
                },
                onCreateNewPreset = {
                    val existingCount = preferences.presetsForMode(sub.mode).size
                    val defaultWork = if (sub.mode == RoutineMode.STANDARD) 3600 else 7200
                    val defaultBreak = if (sub.mode == RoutineMode.STANDARD) 900 else 2700
                    val newPreset = TimerPreset(
                        id = "preset_${sub.mode.name.lowercase()}_${System.currentTimeMillis()}",
                        mode = sub.mode,
                        name = TimerPreset.sanitizeName("Focus ${existingCount + 1}"),
                        workDurationSeconds = defaultWork,
                        breakDurationSeconds = defaultBreak,
                        isDefault = false
                    )
                    onSaveOrUpdatePreset(newPreset)
                    activeSubScreen = SettingsSubScreen.EditPreset(newPreset)
                }
            )
            return
        }
        is SettingsSubScreen.EditPreset -> {
            val latestPreset = preferences.presetsForMode(sub.preset.mode)
                .firstOrNull { it.id == sub.preset.id } ?: sub.preset
            val isCurrentlyActive = preferences.activePresetForMode(sub.preset.mode).id == latestPreset.id
            NativePresetEditorScreen(
                preset = latestPreset,
                isActiveInMode = isCurrentlyActive,
                onUsePreset = {
                    onSelectPreset(latestPreset.mode, latestPreset.id)
                    activeSubScreen = null
                },
                onRenamePreset = { newName ->
                    val updated = latestPreset.copy(name = TimerPreset.sanitizeName(newName))
                    onSaveOrUpdatePreset(updated)
                    activeSubScreen = SettingsSubScreen.EditPreset(updated)
                },
                onEditWorkDuration = {
                    activeSubScreen = SettingsSubScreen.EditPresetWorkDuration(latestPreset)
                },
                onEditBreakDuration = {
                    activeSubScreen = SettingsSubScreen.EditPresetBreakDuration(latestPreset)
                },
                onDeletePreset = {
                    if (!latestPreset.isDefault) {
                        onDeletePreset(latestPreset.mode, latestPreset.id)
                        activeSubScreen = SettingsSubScreen.ModePresetsList(latestPreset.mode)
                    }
                }
            )
            return
        }
        is SettingsSubScreen.EditPresetWorkDuration -> {
            HmsDurationPickerOverlay(
                title = stringResource(R.string.settings_work_duration),
                initialTotalSeconds = sub.preset.workDurationSeconds,
                onConfirmSeconds = { seconds ->
                    val updated = sub.preset.copy(workDurationSeconds = seconds)
                    onSaveOrUpdatePreset(updated)
                    activeSubScreen = SettingsSubScreen.EditPreset(updated)
                },
                onCancel = {
                    activeSubScreen = SettingsSubScreen.EditPreset(sub.preset)
                }
            )
            return
        }
        is SettingsSubScreen.EditPresetBreakDuration -> {
            HmsDurationPickerOverlay(
                title = stringResource(R.string.settings_break_duration),
                initialTotalSeconds = sub.preset.breakDurationSeconds,
                onConfirmSeconds = { seconds ->
                    val updated = sub.preset.copy(breakDurationSeconds = seconds)
                    onSaveOrUpdatePreset(updated)
                    activeSubScreen = SettingsSubScreen.EditPreset(updated)
                },
                onCancel = {
                    activeSubScreen = SettingsSubScreen.EditPreset(sub.preset)
                }
            )
            return
        }
        SettingsSubScreen.GraceDuration -> {
            HmsDurationPickerOverlay(
                title = stringResource(R.string.settings_grace_duration),
                initialTotalSeconds = preferences.readinessGraceSeconds,
                onConfirmSeconds = {
                    onSaveGraceDurationSeconds(it)
                    activeSubScreen = null
                },
                onCancel = { activeSubScreen = null }
            )
            return
        }
        SettingsSubScreen.BackgroundSelector -> {
            NativeBackgroundModeSelectorScreen(
                selectedMode = preferences.backgroundMode,
                onSelectMode = {
                    onSelectBackgroundMode(it)
                    activeSubScreen = null
                }
            )
            return
        }
        SettingsSubScreen.FontSelector -> {
            NativeFontSelectorScreen(
                selectedFont = preferences.fontChoice,
                onSelectFont = {
                    onSelectFontChoice(it)
                    activeSubScreen = null
                }
            )
            return
        }
        SettingsSubScreen.AlertModeSelector -> {
            NativeAlertModeSelectorScreen(
                selectedMode = preferences.alertMode,
                onSelectMode = {
                    onSelectAlertMode(it)
                    activeSubScreen = null
                }
            )
            return
        }
        SettingsSubScreen.SoundSelector -> {
            NativeSoundOptionSelectorScreen(
                selectedSound = preferences.soundOption,
                onSelectSound = { onSelectSoundOption(it) }
            )
            return
        }
        null -> Unit
    }

    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val previewFontFamily = remember(preferences.fontChoice) {
        resolveGoogleFontFamily(preferences.fontChoice)
    }

    val routineModeLabel = when (preferences.routineMode) {
        RoutineMode.STANDARD -> stringResource(R.string.mode_standard)
        RoutineMode.FLEXIBLE -> stringResource(R.string.mode_flexible)
    }

    val activeStandardPreset = preferences.activePresetForMode(RoutineMode.STANDARD)
    val activeFlexiblePreset = preferences.activePresetForMode(RoutineMode.FLEXIBLE)

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
        modifier = modifier.fillMaxSize()
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch {
                        listState.scrollBy(event.verticalScrollPixels)
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.settings_title))
                }
            }

            // Active Timer Mode selector (Standard vs Flexible).
            item {
                Chip(
                    onClick = { activeSubScreen = SettingsSubScreen.ModeSelector },
                    label = { Text(text = stringResource(R.string.settings_routine_mode)) },
                    secondaryLabel = { Text(text = routineModeLabel) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sub-section to configure Standard Mode presets (default: Work 60m active - 15m break).
            item {
                val stdSummary = "${activeStandardPreset.name}: " + stringResource(
                    R.string.preset_summary_format,
                    UserPreferences.formatCompactDuration(activeStandardPreset.workDurationSeconds),
                    UserPreferences.formatCompactDuration(activeStandardPreset.breakDurationSeconds)
                )
                Chip(
                    onClick = {
                        activeSubScreen = SettingsSubScreen.ModePresetsList(RoutineMode.STANDARD)
                    },
                    label = { Text(text = stringResource(R.string.settings_standard_presets)) },
                    secondaryLabel = {
                        Text(
                            text = stdSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sub-section to configure Flexible Mode presets (default: Work 120m active - 45m break).
            item {
                val flexSummary = "${activeFlexiblePreset.name}: " + stringResource(
                    R.string.preset_summary_format,
                    UserPreferences.formatCompactDuration(activeFlexiblePreset.workDurationSeconds),
                    UserPreferences.formatCompactDuration(activeFlexiblePreset.breakDurationSeconds)
                )
                Chip(
                    onClick = {
                        activeSubScreen = SettingsSubScreen.ModePresetsList(RoutineMode.FLEXIBLE)
                    },
                    label = { Text(text = stringResource(R.string.settings_flexible_presets)) },
                    secondaryLabel = {
                        Text(
                            text = flexSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        onClick = { activeSubScreen = SettingsSubScreen.GraceDuration },
                        label = { Text(text = stringResource(R.string.settings_grace_duration)) },
                        secondaryLabel = {
                            Text(text = UserPreferences.formatSecondsAsHms(preferences.readinessGraceSeconds))
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Toggle for the bottom Watch Face OngoingActivity pill ("Now" indicator).
            item {
                ToggleChip(
                    checked = preferences.ongoingPillEnabled,
                    onCheckedChange = onToggleOngoingPill,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_ongoing_pill),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.settings_ongoing_pill_desc),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = { Switch(checked = preferences.ongoingPillEnabled) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3-way Background Mode selector opening a native Wear OS radio list.
            item {
                Chip(
                    onClick = { activeSubScreen = SettingsSubScreen.BackgroundSelector },
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

            // Font Family selector opening a native Wear OS radio list with live per-font typography preview.
            item {
                Chip(
                    onClick = { activeSubScreen = SettingsSubScreen.FontSelector },
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
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Alert mode selector opening a native Wear OS radio list.
            item {
                Chip(
                    onClick = { activeSubScreen = SettingsSubScreen.AlertModeSelector },
                    label = { Text(text = stringResource(R.string.settings_alert_mode)) },
                    secondaryLabel = { Text(text = alertModeLabel) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Non-robotic acoustic sound preset selector opening a native Wear OS radio list.
            item {
                Chip(
                    onClick = { activeSubScreen = SettingsSubScreen.SoundSelector },
                    label = { Text(text = stringResource(R.string.settings_sound_option)) },
                    secondaryLabel = { Text(text = soundOptionLabel) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Independent alarm volume header and native InlineSlider.
            item {
                ListHeader {
                    Text(
                        text = stringResource(
                            R.string.settings_volume,
                            (preferences.alertVolume * 100f).roundToInt()
                        )
                    )
                }
            }

            item {
                InlineSlider(
                    value = preferences.alertVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.0f..1.0f,
                    steps = 9,
                    decreaseIcon = {
                        Icon(
                            imageVector = InlineSliderDefaults.Decrease,
                            contentDescription = null
                        )
                    },
                    increaseIcon = {
                        Icon(
                            imageVector = InlineSliderDefaults.Increase,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Preview sound and haptics button.
            item {
                Chip(
                    onClick = onPreviewAlert,
                    label = { Text(text = stringResource(R.string.settings_test_alert)) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Re-openable Interactive Tutorial button.
            item {
                Chip(
                    onClick = onOpenTutorial,
                    label = { Text(text = stringResource(R.string.settings_open_tutorial)) },
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

// Native Wear OS Radio Selector for RoutineMode (Standard vs Flexible).
@Composable
private fun NativeRoutineModeSelectorScreen(
    selectedMode: RoutineMode,
    onSelectMode: (RoutineMode) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.settings_routine_mode))
                }
            }
            items(RoutineMode.entries) { mode ->
                val label = when (mode) {
                    RoutineMode.STANDARD -> stringResource(R.string.mode_standard)
                    RoutineMode.FLEXIBLE -> stringResource(R.string.mode_flexible)
                }
                val desc = when (mode) {
                    RoutineMode.STANDARD -> stringResource(R.string.mode_standard_desc)
                    RoutineMode.FLEXIBLE -> stringResource(R.string.mode_flexible_desc)
                }
                val isSelected = mode == selectedMode
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { onSelectMode(mode) },
                    label = { Text(text = label) },
                    secondaryLabel = { Text(text = desc, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    toggleControl = { RadioButton(selected = isSelected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Native Wear OS list of presets for a specific mode (Standard or Flexible) with "+ New Preset".
@Composable
private fun NativeModePresetsListScreen(
    mode: RoutineMode,
    preferences: UserPreferences,
    onOpenPresetEditor: (TimerPreset) -> Unit,
    onCreateNewPreset: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val presets = preferences.presetsForMode(mode)
    val activePresetId = preferences.activePresetForMode(mode).id

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val headerTitle = if (mode == RoutineMode.STANDARD) {
        stringResource(R.string.settings_standard_presets)
    } else {
        stringResource(R.string.settings_flexible_presets)
    }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = headerTitle)
                }
            }
            items(presets, key = { it.id }) { preset ->
                val isActive = preset.id == activePresetId
                val summary = stringResource(
                    R.string.preset_summary_format,
                    UserPreferences.formatCompactDuration(preset.workDurationSeconds),
                    UserPreferences.formatCompactDuration(preset.breakDurationSeconds)
                )
                Chip(
                    onClick = { onOpenPresetEditor(preset) },
                    label = {
                        Text(
                            text = if (isActive) "* ${preset.name}" else preset.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = summary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = if (isActive) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = onCreateNewPreset,
                    label = { Text(text = stringResource(R.string.preset_add_new)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Native Wear OS Preset Editor Screen supporting custom name input (max 16 chars, [A-Za-z0-9 -_]),
// HH:MM:SS Work/Break duration pickers, active selection, and deletion of non-default presets.
@Composable
private fun NativePresetEditorScreen(
    preset: TimerPreset,
    isActiveInMode: Boolean,
    onUsePreset: () -> Unit,
    onRenamePreset: (String) -> Unit,
    onEditWorkDuration: () -> Unit,
    onEditBreakDuration: () -> Unit,
    onDeletePreset: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val remoteInputKey = "preset_custom_name_input"
    val remoteInputTitle = stringResource(R.string.preset_name_label)

    val remoteInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val resultsBundle: Bundle? = if (data != null) {
                RemoteInput.getResultsFromIntent(data)
            } else {
                null
            }
            val rawText = resultsBundle?.getCharSequence(remoteInputKey)?.toString()
            if (!rawText.isNullOrBlank()) {
                onRenamePreset(TimerPreset.sanitizeName(rawText, fallback = preset.name))
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.preset_edit_title))
                }
            }

            item {
                ToggleChip(
                    checked = isActiveInMode,
                    onCheckedChange = { onUsePreset() },
                    label = { Text(text = stringResource(R.string.preset_use_now)) },
                    toggleControl = { RadioButton(selected = isActiveInMode) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Custom name input using native Wear OS RemoteInput keyboard/voice input.
            item {
                Chip(
                    onClick = {
                        val remoteInput = RemoteInput.Builder(remoteInputKey)
                            .setLabel(remoteInputTitle)
                            .build()
                        val intent = Intent("android.support.wearable.input.action.REMOTE_INPUT").apply {
                            putExtra(
                                "android.support.wearable.input.extra.REMOTE_INPUTS",
                                arrayOf(remoteInput)
                            )
                            putExtra("android.support.wearable.input.extra.TITLE", remoteInputTitle)
                        }
                        runCatching {
                            remoteInputLauncher.launch(intent)
                        }
                    },
                    label = { Text(text = stringResource(R.string.preset_name_label)) },
                    secondaryLabel = {
                        Text(
                            text = preset.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = onEditWorkDuration,
                    label = { Text(text = stringResource(R.string.settings_work_duration)) },
                    secondaryLabel = {
                        Text(text = UserPreferences.formatSecondsAsHms(preset.workDurationSeconds))
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = onEditBreakDuration,
                    label = { Text(text = stringResource(R.string.settings_break_duration)) },
                    secondaryLabel = {
                        Text(text = UserPreferences.formatSecondsAsHms(preset.breakDurationSeconds))
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Default presets cannot be deleted; user-created presets show the native Delete Chip.
            if (!preset.isDefault) {
                item {
                    Chip(
                        onClick = onDeletePreset,
                        label = { Text(text = stringResource(R.string.preset_delete)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                item {
                    Chip(
                        onClick = {},
                        enabled = false,
                        label = { Text(text = stringResource(R.string.preset_default_badge)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeBackgroundModeSelectorScreen(
    selectedMode: BackgroundMode,
    onSelectMode: (BackgroundMode) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.settings_bg_mode))
                }
            }
            items(BackgroundMode.entries) { mode ->
                val label = when (mode) {
                    BackgroundMode.MESH_3D -> stringResource(R.string.bg_mode_3d)
                    BackgroundMode.MESH_2D -> stringResource(R.string.bg_mode_2d)
                    BackgroundMode.SOLID_COLOR -> stringResource(R.string.bg_mode_solid)
                }
                val isSelected = mode == selectedMode
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { onSelectMode(mode) },
                    label = { Text(text = label) },
                    toggleControl = { RadioButton(selected = isSelected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NativeFontSelectorScreen(
    selectedFont: FontChoice,
    onSelectFont: (FontChoice) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.settings_font_label))
                }
            }
            items(FontChoice.entries) { font ->
                val fontFamily = remember(font) { resolveGoogleFontFamily(font) }
                val isSelected = font == selectedFont
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { onSelectFont(font) },
                    label = {
                        Text(
                            text = font.displayName,
                            style = TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.settings_font_preview_sample),
                            style = TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    },
                    toggleControl = { RadioButton(selected = isSelected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NativeAlertModeSelectorScreen(
    selectedMode: AlertMode,
    onSelectMode: (AlertMode) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.settings_alert_mode))
                }
            }
            items(AlertMode.entries) { mode ->
                val label = when (mode) {
                    AlertMode.SOUND_AND_HAPTICS -> stringResource(R.string.alert_mode_all)
                    AlertMode.SOUND_ONLY -> stringResource(R.string.alert_mode_sound)
                    AlertMode.HAPTICS_ONLY -> stringResource(R.string.alert_mode_haptics)
                    AlertMode.NONE -> stringResource(R.string.alert_mode_none)
                }
                val isSelected = mode == selectedMode
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { onSelectMode(mode) },
                    label = { Text(text = label) },
                    toggleControl = { RadioButton(selected = isSelected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NativeSoundOptionSelectorScreen(
    selectedSound: SoundOption,
    onSelectSound: (SoundOption) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.settings_sound_option))
                }
            }
            items(SoundOption.entries) { sound ->
                val label = when (sound) {
                    SoundOption.SINGING_BOWL -> stringResource(R.string.sound_singing_bowl)
                    SoundOption.WARM_MARIMBA -> stringResource(R.string.sound_warm_marimba)
                    SoundOption.CRYSTAL_CHIME -> stringResource(R.string.sound_crystal_chime)
                    SoundOption.SOFT_GONG -> stringResource(R.string.sound_soft_gong)
                    SoundOption.SYSTEM_ALARM -> stringResource(R.string.sound_system_alarm)
                }
                val isSelected = sound == selectedSound
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { onSelectSound(sound) },
                    label = { Text(text = label) },
                    toggleControl = { RadioButton(selected = isSelected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Full-screen Wear OS 3-column drum picker using native Picker and circular Wear OS Button controls.
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
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption2,
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.picker_hours),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.primary
                )
                Picker(
                    state = hoursState,
                    contentDescription = stringResource(R.string.picker_hours),
                    modifier = Modifier.fillMaxSize()
                ) { hour ->
                    Text(
                        text = String.format("%02d", hour),
                        style = MaterialTheme.typography.display3
                    )
                }
            }

            Text(
                text = ":",
                style = MaterialTheme.typography.display3,
                modifier = Modifier.padding(top = 12.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.picker_minutes),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.primary
                )
                Picker(
                    state = minutesState,
                    contentDescription = stringResource(R.string.picker_minutes),
                    modifier = Modifier.fillMaxSize()
                ) { minute ->
                    Text(
                        text = String.format("%02d", minute),
                        style = MaterialTheme.typography.display3
                    )
                }
            }

            Text(
                text = ":",
                style = MaterialTheme.typography.display3,
                modifier = Modifier.padding(top = 12.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.picker_seconds),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.primary
                )
                Picker(
                    state = secondsState,
                    contentDescription = stringResource(R.string.picker_seconds),
                    modifier = Modifier.fillMaxSize()
                ) { second ->
                    Text(
                        text = String.format("%02d", second),
                        style = MaterialTheme.typography.display3
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.picker_cancel)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    val total = (hoursState.selectedOption * 3600) +
                        (minutesState.selectedOption * 60) +
                        secondsState.selectedOption
                    onConfirmSeconds(total.coerceAtLeast(1))
                },
                colors = ButtonDefaults.primaryButtonColors(),
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.picker_save)
                )
            }
        }
    }
}
