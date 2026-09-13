package com.Badnng.moe.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import com.Badnng.moe.ui.theme.NonPredictiveBackInterceptor

@Composable
fun MiuixScheduledNotificationSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    var showSheet by remember { mutableStateOf(show) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val sheetHeightPx = remember { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val blurProgress = remember { Animatable(0f) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(Unit) {
        snapshotFlow { blurProgress.value }
            .collect { BlurState.updateProgress(it) }
    }

    LaunchedEffect(show) {
        if (show) {
            showSheet = true
            BlurState.show()
            dragProgress = -1f
            blurProgress.snapTo(0f)
            blurProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
            )
        } else {
            showSheet = false
        }
    }

    LaunchedEffect(dragProgress) {
        if (dragProgress in 0f..1f) {
            blurProgress.snapTo(dragProgress)
        }
    }

    LaunchedEffect(showSheet) {
        if (!showSheet) {
            blurProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { BlurState.hide() }
    }

    var showCustomTimePicker by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)) }

    WindowBottomSheet(
        show = showSheet,
        title = "选择推送时间",
        enableWindowDim = false,
        allowDismiss = true,
        enableNestedScroll = true,
        onDismissRequest = { showSheet = false },
        onDismissFinished = {
            BlurState.hide()
            onDismiss()
        },
    ) {
        NonPredictiveBackInterceptor()
        if (showSheet) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .onGloballyPositioned { coordinates ->
                        val sheetTop = coordinates.positionInWindow().y
                        dragProgress = 1f - (sheetTop / sheetHeightPx).coerceIn(0f, 1f)
                    },
            )
        }
        val dismiss = LocalDismissState.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(
                targetState = showCustomTimePicker,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(300)
                    )) togetherWith (fadeOut(animationSpec = tween(200)) + slideOutVertically(
                        targetOffsetY = { -it / 4 },
                        animationSpec = tween(200)
                    )) using SizeTransform(clip = false)
                },
                label = "timePickerSwitch"
            ) { customMode ->
                if (!customMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val presets = listOf(
                            "5 分钟" to 5L,
                            "10 分钟" to 10L,
                            "30 分钟" to 30L,
                            "1 小时" to 60L,
                            "2 小时" to 120L
                        )
                        presets.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (label, minutes) ->
                                    TextButton(
                                        text = label,
                                        onClick = {
                                            performHaptic()
                                            val triggerAt = System.currentTimeMillis() + minutes * 60 * 1000
                                            onSchedule(triggerAt)
                                            dismiss?.invoke()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        HorizontalDivider(color = MiuixTheme.colorScheme.outline)
                        TextButton(
                            text = "自定义时间",
                            onClick = { performHaptic(); showCustomTimePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NumberPicker(
                                value = selectedHour,
                                onValueChange = { selectedHour = it },
                                range = 0..23,
                                label = { it.toString().padStart(2, '0') },
                                wrapAround = true,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = ":",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            NumberPicker(
                                value = selectedMinute,
                                onValueChange = { selectedMinute = it },
                                range = 0..59,
                                label = { it.toString().padStart(2, '0') },
                                wrapAround = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                text = "返回",
                                onClick = { performHaptic(); showCustomTimePicker = false },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "确认",
                                onClick = {
                                    performHaptic()
                                    val now = java.util.Calendar.getInstance()
                                    val target = java.util.Calendar.getInstance().apply {
                                        set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                                        set(java.util.Calendar.MINUTE, selectedMinute)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                    }
                                    if (target.before(now)) {
                                        target.add(java.util.Calendar.DAY_OF_MONTH, 1)
                                    }
                                    onSchedule(target.timeInMillis)
                                    dismiss?.invoke()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
