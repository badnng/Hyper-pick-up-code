package com.Badnng.moe.ui.screen

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import com.Badnng.moe.helper.ScreenshotStorage
import com.Badnng.moe.ui.LocalAppUi
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullScreenImageDialog(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.statusBarColor = AndroidColor.TRANSPARENT
                it.navigationBarColor = AndroidColor.TRANSPARENT
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
                WindowCompat.getInsetsController(it, dialogView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            onDispose { }
        }

        BackHandler(onBack = onDismiss)

        val context = LocalContext.current
        val imageSize = remember(context, imagePath) {
            ScreenshotStorage.decodeBounds(context, imagePath)
                ?.let { (width, height) -> IntSize(width, height) }
                ?: IntSize(1, 1)
        }
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var controlsVisible by remember { mutableStateOf(true) }

        fun clampedOffset(candidate: Offset, targetScale: Float): Offset {
            if (viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero
            val fit = min(
                viewportSize.width.toFloat() / imageSize.width,
                viewportSize.height.toFloat() / imageSize.height,
            )
            val displayedWidth = imageSize.width * fit * targetScale
            val displayedHeight = imageSize.height * fit * targetScale
            val maxX = max(0f, (displayedWidth - viewportSize.width) / 2f)
            val maxY = max(0f, (displayedHeight - viewportSize.height) / 2f)
            return Offset(
                x = candidate.x.coerceIn(-maxX, maxX),
                y = candidate.y.coerceIn(-maxY, maxY),
            )
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
            scale = nextScale
            offset = if (nextScale == 1f) {
                Offset.Zero
            } else {
                clampedOffset(offset + panChange, nextScale)
            }
        }

        LaunchedEffect(viewportSize, imageSize) {
            offset = clampedOffset(offset, scale)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewportSize = it }
                .pointerInput(viewportSize) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { tapPosition ->
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(
                                    viewportSize.width / 2f,
                                    viewportSize.height / 2f,
                                )
                                scale = targetScale
                                offset = clampedOffset(
                                    (center - tapPosition) * (targetScale - 1f),
                                    targetScale,
                                )
                            }
                        },
                    )
                }
                .transformable(transformState),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ScreenshotStorage.imageModel(imagePath),
                contentDescription = "识别截图大图",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        LocalAppUi.current.fullScreenImageCloseButton(onDismiss)
                    }
                }
            }
        }
    }
}
