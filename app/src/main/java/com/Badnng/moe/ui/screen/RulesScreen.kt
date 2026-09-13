package com.Badnng.moe.ui.screen

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.Badnng.moe.ui.component.Md3eNavigationRailExpandButton
import com.Badnng.moe.ui.component.SimpleRuleCenterContent
import com.Badnng.moe.ui.component.SimpleRuleCenterPage
import com.Badnng.moe.ui.component.SimpleRuleCenterState
import com.Badnng.moe.ui.component.rememberSimpleRuleCenterState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onExpandNavigationRail: (() -> Unit)? = null,
    onSubPageChange: (Boolean) -> Unit = {},
    onShowMenu: ((position: androidx.compose.ui.geometry.Offset, rename: (() -> Unit)?, delete: (() -> Unit)?, export: (() -> Unit)?) -> Unit)? = null,
    onDismissMenu: (() -> Unit)? = null,
) {
    val state = rememberSimpleRuleCenterState()
    val motionScheme = MaterialTheme.motionScheme
    // 标题由页面内容在同一份状态中决定；规则包加载前显示通用标题。
    val isSubPage = state.canGoBack
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var predictiveBackEnabled by remember {
        mutableStateOf(prefs.getBoolean("predictive_back_enabled", true))
    }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "predictive_back_enabled") {
                predictiveBackEnabled = prefs.getBoolean(key, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) {
        val multiplier = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        backProgress * 100f * multiplier
    } else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    PredictiveBackHandler(
        enabled = predictiveBackEnabled && isSubPage,
    ) { backEvent: Flow<BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            state.back()
        } catch (e: CancellationException) {
            // 手势取消时保持原状态，不做返回
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    BackHandler(enabled = !predictiveBackEnabled && isSubPage) { state.back() }
    LaunchedEffect(isSubPage) { onSubPageChange(isSubPage) }
    DisposableEffect(Unit) {
        onDispose { onSubPageChange(false) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (isSubPage) state.title else "规则") },
                    navigationIcon = {
                        if (isSubPage) {
                            IconButton(onClick = { state.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                        } else {
                            onExpandNavigationRail?.let { Md3eNavigationRailExpandButton(onClick = it) }
                        }
                    },
                )
            },
        ) { innerPadding ->
            SimpleRuleCenterContent(
                state = state,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                isMiuix = false,
                modifier = Modifier.fillMaxSize(),
                onBackPage = { state.back() },
                pageOverride = if (isSubPage) SimpleRuleCenterPage.Root else null,
            )
        }

        AnimatedVisibility(
            visible = isSubPage,
            enter = slideInHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            ) { it } + fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
            exit = slideOutHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            ) { it } + fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        this.translationX = currentTranslationX
                        shape = RoundedCornerShape(currentCornerRadius)
                        clip = true
                    }
                    .border(
                        width = if (isPredictiveBackInProgress) 1.dp else 0.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress),
                        shape = RoundedCornerShape(currentCornerRadius),
                    )
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(state.title) },
                            navigationIcon = {
                                IconButton(onClick = { state.back() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = state.stack.size to state.page,
                        transitionSpec = {
                            val spatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
                            val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
                            val forward = targetState.first > initialState.first
                            if (forward) {
                                (slideInHorizontally(spatialSpec) { it } + fadeIn(effectsSpec)) togetherWith
                                    (slideOutHorizontally(spatialSpec) { -it / 4 } + fadeOut(effectsSpec))
                            } else {
                                (slideInHorizontally(spatialSpec) { -it / 4 } + fadeIn(effectsSpec)) togetherWith
                                    (slideOutHorizontally(spatialSpec) { it } + fadeOut(effectsSpec))
                            }
                        },
                        label = "md3eRuleSubPage",
                    ) { (_, page) ->
                        SimpleRuleCenterContent(
                            state = state,
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                            ),
                            isMiuix = false,
                            modifier = Modifier.fillMaxSize(),
                            onBackPage = { state.back() },
                            pageOverride = page,
                        )
                    }
                }
            }
        }
    }
}
