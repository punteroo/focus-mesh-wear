package com.focusmesh.wear.ui.screens

import androidx.activity.compose.BackHandler
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
import com.focusmesh.wear.domain.model.SoundOption
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.ui.theme.resolveGoogleFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface SettingsSubScreen {
    data object WorkDuration : SettingsSubScreen
    data object BreakDuration : SettingsSubScreen
    data object GraceDuration : SettingsSubScreen
    data object BackgroundSelector : SettingsSubScreen
    data object FontSelector : SettingsSubScreen
    data object AlertModeSelector : SettingsSubScreen
    data object SoundSelector : SettingsSubScreen
}

// Native Wear OS configuration drawer using official Wear Compose Material primitives:
// ScalingLazyColumn with rotary bezel scroll, ListHeader, Chip, ToggleChip with Switch/RadioButton,
// InlineSlider with InlineSliderDefaults icons, and native 3-column Picker with circular Buttons.
@Composable
fun SettingsDrawerScreen(
    preferences: UserPreferences,
    isBatterySaverActive: Boolean,
    onSelectBackgroundMode: (BackgroundMode) -> Unit,
    onToggleTouchWave: (Boolean) -> Unit,
    onToggleOngoingPill: (Boolean) -> Unit,
    onToggleReadinessGrace: (Boolean) -> Unit,
    onSaveWorkDurationSeconds: (Int) -> Unit,
    onSaveBreakDurationSeconds: (Int) -> Unit,
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

    // Intercept Back gesture when a native sub-menu or picker is open to return to the main Settings list.
    BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = null
    }

    when (activeSubScreen) {
        SettingsSubScreen.WorkDuration -> {
            HmsDurationPickerOverlay(
                title = stringResource(R.string.settings_work_duration),
                initialTotalSeconds = preferences.workDurationSeconds,
                onConfirmSeconds = {
                    onSaveWorkDurationSeconds(it)
                    activeSubScreen = null
                },
                onCancel = { activeSubScreen = null }
            )
            return
        }
        SettingsSubScreen.BreakDuration -> {
            HmsDurationPickerOverlay(
                title = stringResource(R.string.settings_break_duration),
                initialTotalSeconds = preferences.breakDurationSeconds,
                onConfirmSeconds = {
                    onSaveBreakDurationSeconds(it)
                    activeSubScreen = null
                },
                onCancel = { activeSubScreen = null }
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

            // Custom Work Duration (Hour : Minute : Second granularity).
            item {
                Chip(
                    onClick = { activeSubScreen = SettingsSubScreen.WorkDuration },
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
                    onClick = { activeSubScreen = SettingsSubScreen.BreakDuration },
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

            // Toggle for the bottom Watch Face OngoingActivity pill ("Now" indicator), allowing users to
            // choose either the bottom pill, the native Watch Face Complication, or both.
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
