package com.focusmesh.wear.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.BackgroundMode
import com.focusmesh.wear.domain.model.RoutineMode
import com.focusmesh.wear.domain.model.TimerPhase
import com.focusmesh.wear.domain.model.TimerState
import com.focusmesh.wear.domain.model.UserPreferences
import com.focusmesh.wear.ui.mesh.MeshBackgroundCanvas
import com.focusmesh.wear.ui.mesh.WavePhysicsState
import com.focusmesh.wear.ui.theme.paletteForTimerState
import com.focusmesh.wear.ui.theme.resolveGoogleFontFamily
import kotlinx.coroutines.launch
import kotlin.math.abs

// Renders the two vertically interchangeable timer screens:
// - Page 0: Standard Mode timer screen (sequential Work -> Break -> Readiness cycle)
// - Page 1: Flexible Mode timer screen (dual-pool Work/Break screen with dual concentric bezel rings
//   and a notable FLEXIBLE mode indicator badge)
// Pulling down or swiping up vertically swaps between the Standard Mode and Flexible Mode screens.
// On Flexible Mode, a single tap on the clock pauses/starts like normal, and long-tapping the clock
// swaps between Active and Break while automatically unpausing the swapped-in timer.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerScreen(
    timerState: TimerState,
    preferences: UserPreferences,
    isBatterySaverActive: Boolean,
    isAmbient: Boolean,
    onPrimaryTap: () -> Unit,
    onLongPressSkip: () -> Unit,
    onSelectPreset: (RoutineMode, String) -> Unit,
    onSelectRoutineMode: (RoutineMode) -> Unit,
    onOpenSettingsFromRightEdge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetSelector by remember { mutableStateOf(false) }

    BackHandler(enabled = showPresetSelector) {
        showPresetSelector = false
    }

    if (showPresetSelector && !isAmbient) {
        NativeMainScreenPresetSelector(
            preferences = preferences,
            onSelectPreset = { presetId ->
                onSelectPreset(preferences.routineMode, presetId)
                showPresetSelector = false
            }
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val targetPage = if (preferences.routineMode == RoutineMode.FLEXIBLE) 1 else 0
    val verticalPagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { 2 }
    )

    // Keep the vertical pager synchronized if RoutineMode changes externally.
    LaunchedEffect(targetPage) {
        if (verticalPagerState.currentPage != targetPage) {
            verticalPagerState.animateScrollToPage(targetPage)
        }
    }

    // Update RoutineMode when vertical page settles on Standard (0) or Flexible (1).
    LaunchedEffect(verticalPagerState.settledPage) {
        val settledMode = if (verticalPagerState.settledPage == 1) {
            RoutineMode.FLEXIBLE
        } else {
            RoutineMode.STANDARD
        }
        if (settledMode != preferences.routineMode) {
            onSelectRoutineMode(settledMode)
        }
    }

    VerticalPager(
        state = verticalPagerState,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) { page ->
        val pageMode = if (page == 1) RoutineMode.FLEXIBLE else RoutineMode.STANDARD
        ModeTimerPageContent(
            pageMode = pageMode,
            timerState = timerState,
            preferences = preferences,
            isBatterySaverActive = isBatterySaverActive,
            isAmbient = isAmbient,
            onPrimaryTap = onPrimaryTap,
            onLongPressClock = onLongPressSkip,
            onOpenPresetSelector = { showPresetSelector = true },
            onOpenSettingsFromRightEdge = onOpenSettingsFromRightEdge,
            onVerticalModeSwap = {
                val nextPage = if (verticalPagerState.currentPage == 0) 1 else 0
                val nextMode = if (nextPage == 1) RoutineMode.FLEXIBLE else RoutineMode.STANDARD
                onSelectRoutineMode(nextMode)
                coroutineScope.launch {
                    verticalPagerState.animateScrollToPage(nextPage)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeTimerPageContent(
    pageMode: RoutineMode,
    timerState: TimerState,
    preferences: UserPreferences,
    isBatterySaverActive: Boolean,
    isAmbient: Boolean,
    onPrimaryTap: () -> Unit,
    onLongPressClock: () -> Unit,
    onOpenPresetSelector: () -> Unit,
    onOpenSettingsFromRightEdge: () -> Unit,
    onVerticalModeSwap: () -> Unit
) {
    val isFlexibleScreen = pageMode == RoutineMode.FLEXIBLE
    val wavePhysicsState = remember(pageMode) { WavePhysicsState(gridSize = 17) }
    val fontFamily = remember(preferences.fontChoice) {
        resolveGoogleFontFamily(preferences.fontChoice)
    }
    val effectiveBgMode = if (isBatterySaverActive) {
        BackgroundMode.SOLID_COLOR
    } else {
        preferences.backgroundMode
    }
    val isMeshInteractive = effectiveBgMode != BackgroundMode.SOLID_COLOR &&
        preferences.touchWaveEnabled &&
        !isAmbient

    val targetPalette = paletteForTimerState(
        phase = timerState.phase,
        isRunning = timerState.isRunning,
        isAwaitingManualContinue = timerState.isAwaitingManualContinue
    )

    val animatedAccentColor by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "timerAccentColor"
    )
    val accentColor = if (isAmbient) targetPalette.primary else animatedAccentColor

    var hasInitializedWave by remember { mutableStateOf(false) }
    LaunchedEffect(timerState.isRunning, timerState.phase, pageMode) {
        if (hasInitializedWave && !isAmbient && effectiveBgMode != BackgroundMode.SOLID_COLOR) {
            val pulseIntensity = if (timerState.isRunning) 1.05f else 0.65f
            wavePhysicsState.triggerStateTransitionWave(intensity = pulseIntensity)
        }
        hasInitializedWave = true
    }

    val timerScale by animateFloatAsState(
        targetValue = if (timerState.isRunning) 1.0f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "timerDigitsScale"
    )

    val breathingAlpha = if (isAmbient) {
        1.0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "timerBreathing")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathingAlpha"
        )
        animatedAlpha
    }

    val clockInteractionSource = remember { MutableInteractionSource() }
    val presetInteractionSource = remember { MutableInteractionSource() }

    val topHeaderLabel = when (timerState.phase) {
        TimerPhase.WORK -> timerState.activePresetName
        TimerPhase.BREAK -> "${timerState.activePresetName} - ${stringResource(R.string.phase_break)}"
        TimerPhase.READINESS -> stringResource(R.string.phase_readiness)
    }

    val centerDisplayText = when {
        timerState.phase == TimerPhase.READINESS && !preferences.readinessGraceEnabled ->
            stringResource(R.string.phase_readiness)
        isAmbient -> timerState.formattedAmbientTime()
        else -> timerState.formattedTime()
    }

    val subHintText = when {
        isAmbient -> null
        timerState.phase == TimerPhase.READINESS && timerState.isAwaitingManualContinue ->
            stringResource(R.string.hint_tap_to_start)
        !timerState.isRunning -> stringResource(R.string.hint_paused)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isMeshInteractive, isAmbient, pageMode) {
                if (isAmbient) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val startedInRightEdgeZone = size.width > 0 && startX >= size.width * 0.84f
                    val startedInVerticalEdgeZone = size.height > 0 &&
                        (startY <= size.height * 0.22f || startY >= size.height * 0.78f)
                    var gestureTriggered = false

                    if (!startedInRightEdgeZone && !startedInVerticalEdgeZone && isMeshInteractive && size.width > 0 && size.height > 0) {
                        val normX = ((down.position.x / size.width) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                        val normY = ((down.position.y / size.height) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                        wavePhysicsState.applyTouchImpulse(normX, normY, strength = 0.58f)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val pos = change.position

                        if (startedInRightEdgeZone) {
                            val totalDx = pos.x - startX
                            if (!gestureTriggered && totalDx < -size.width * 0.14f) {
                                gestureTriggered = true
                                change.consume()
                                onOpenSettingsFromRightEdge()
                            }
                        } else if (startedInVerticalEdgeZone) {
                            // Pulling down or swiping up vertically swaps between Standard Mode and Flexible Mode screens.
                            val totalDy = pos.y - startY
                            val totalDx = pos.x - startX
                            if (!gestureTriggered && abs(totalDy) > size.height * 0.15f && abs(totalDy) > abs(totalDx)) {
                                gestureTriggered = true
                                change.consume()
                                onVerticalModeSwap()
                            }
                        } else if (isMeshInteractive && size.width > 0 && size.height > 0) {
                            val normX = ((pos.x / size.width) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                            val normY = ((pos.y / size.height) * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                            wavePhysicsState.applyTouchImpulse(normX, normY, strength = 0.58f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        MeshBackgroundCanvas(
            phase = timerState.phase,
            isRunning = timerState.isRunning,
            isAwaitingManualContinue = timerState.isAwaitingManualContinue,
            progressFraction = timerState.progressFraction(),
            isFlexibleMode = isFlexibleScreen,
            standbyProgressFraction = timerState.standbyProgressFraction(),
            backgroundMode = preferences.backgroundMode,
            isBatterySaverActive = isBatterySaverActive,
            isAmbient = isAmbient,
            wavePhysicsState = wavePhysicsState
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(if (isAmbient) 1.0f else timerScale)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Tappable Preset selector pill above the timer showing the preset name instead of hardcoded "Work".
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clickable(
                        interactionSource = presetInteractionSource,
                        indication = null,
                        enabled = !isAmbient,
                        onClick = onOpenPresetSelector
                    )
                    .background(
                        color = if (isAmbient) Color.Transparent else Color.Black.copy(alpha = 0.32f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                if (!isAmbient) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = accentColor.copy(
                                    alpha = if (timerState.isRunning) breathingAlpha else 0.85f
                                ),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = topHeaderLabel,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        color = if (isAmbient) {
                            accentColor.copy(alpha = 0.65f)
                        } else {
                            accentColor.copy(alpha = 0.96f)
                        }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Central time readout:
            // - Single tap: starts or pauses the timer.
            // - Long tap: in Flexible Mode, swaps between Active and Break and automatically unpauses;
            //   in Standard Mode, skips to the next phase.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = clockInteractionSource,
                        indication = null,
                        onClick = onPrimaryTap,
                        onLongClick = onLongPressClock
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = centerDisplayText,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (centerDisplayText.length > 5) 34.sp else 46.sp,
                        letterSpacing = 1.0.sp,
                        color = when {
                            isAmbient -> Color.White.copy(alpha = 0.82f)
                            timerState.isRunning -> Color.White
                            else -> Color.White.copy(alpha = 0.78f + 0.18f * breathingAlpha)
                        }
                    ),
                    textAlign = TextAlign.Center
                )

                if (subHintText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subHintText,
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = accentColor.copy(alpha = 0.65f + 0.30f * breathingAlpha)
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Notable Flexible Mode indicator badge + standby timer readout shown on the Flexible Mode screen.
            if (isFlexibleScreen && !isAmbient) {
                Spacer(modifier = Modifier.height(4.dp))
                val standbyColor = if (timerState.phase == TimerPhase.WORK) {
                    Color(0xFF38D6FF)
                } else {
                    Color(0xFFFF4D5A)
                }
                val standbyText = if (timerState.phase == TimerPhase.WORK) {
                    stringResource(R.string.flexible_badge_break, timerState.formattedStandbyPoolTime())
                } else {
                    stringResource(R.string.flexible_badge_work, timerState.formattedStandbyPoolTime())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            color = Color(0xFF0A0E16).copy(alpha = 0.78f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.2.dp,
                            color = standbyColor.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.flexible_mode_indicator),
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            color = standbyColor
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = standbyText,
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    )
                }
            }
        }
    }
}

// Native Wear OS Preset Selector opened by tapping the preset name above the timer.
@Composable
private fun NativeMainScreenPresetSelector(
    preferences: UserPreferences,
    onSelectPreset: (String) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val presets = preferences.presetsForMode(preferences.routineMode)
    val activePreset = preferences.activePresetForMode(preferences.routineMode)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C10))
                .onRotaryScrollEvent { event ->
                    coroutineScope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.presets_select_title))
                }
            }
            items(presets, key = { it.id }) { preset ->
                val isSelected = preset.id == activePreset.id
                val summary = stringResource(
                    R.string.preset_summary_format,
                    UserPreferences.formatCompactDuration(preset.workDurationSeconds),
                    UserPreferences.formatCompactDuration(preset.breakDurationSeconds)
                )
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { onSelectPreset(preset.id) },
                    label = {
                        Text(
                            text = preset.name,
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
                    toggleControl = { RadioButton(selected = isSelected) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
