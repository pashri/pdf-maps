package com.pashri.pdfmaps.ui

import android.graphics.Rect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pashri.pdfmaps.R
import com.pashri.pdfmaps.render.TileRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Highest zoom, as a multiple of the fit-to-screen scale. */
private const val MAX_ZOOM_MULTIPLE = 24f

/**
 * The zoom stops a double tap cycles through, as multiples of the
 * fit-to-screen scale. Tapping past the last stop returns to fit.
 */
private val DOUBLE_TAP_STOPS = listOf(3f, 6f)

/** How close two scales must be to count as the same zoom stop. */
private const val STOP_TOLERANCE = 1.05f

/** Duration of the double-tap zoom animation, in milliseconds. */
private const val ZOOM_ANIMATION_MS = 280

/**
 * Opacity of the disc behind the back arrow. Enough to separate it
 * from map content without hiding what is underneath.
 */
private const val BACK_SCRIM_ALPHA = 0.7f

/**
 * Full-screen viewer for one map page.
 *
 * @param onBack Called when the user navigates back.
 * @param viewModel Backing view model.
 */
@Composable
fun MapViewerScreen(
    onBack: () -> Unit,
    viewModel: MapViewerViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Backgrounding is the moment before the process may be killed,
    // and disposal covers navigating back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.saveViewport()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.saveViewport() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when (val current = state) {
            is ViewerUiState.Loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
            )

            is ViewerUiState.Failed -> Text(
                text = current.reason,
                modifier = Modifier.align(Alignment.Center),
            )

            is ViewerUiState.Ready -> TiledPage(
                state = current,
                savedViewport = { viewModel.viewport },
                onViewportChanged = viewModel::onViewportChanged,
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = BACK_SCRIM_ALPHA),
                    shape = CircleShape,
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    }
}

/**
 * The zoomable, pannable page.
 *
 * Screen position maps to page position as
 * `screen = page * scale + offset`, so [scale] is always absolute
 * pixels per PDF point and [offset] is a pixel translation.
 *
 * @param state Open document and page metrics.
 * @param savedViewport Reads the last known position. A function
 *   rather than a value because it is re-read when the viewport is
 *   re-measured, which is how rotation keeps the user in place.
 * @param onViewportChanged Called with every change of zoom or
 *   centre.
 */
@Composable
private fun TiledPage(
    state: ViewerUiState.Ready,
    savedViewport: () -> Viewport?,
    onViewportChanged: (Viewport) -> Unit,
) {
    val pageWidth = state.pageSize.width
    val pageHeight = state.pageSize.height

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()
        val fitScale = minOf(
            viewWidth / pageWidth,
            viewHeight / pageHeight,
        )
        val maxScale = fitScale * MAX_ZOOM_MULTIPLE

        val renderer = remember(state.entry.id, fitScale) {
            TileRenderer(
                source = state.source,
                pageIndex = state.entry.pageIndex,
                fitScale = fitScale,
                cache = state.cache,
            )
        }

        var scale by remember(state.entry.id, fitScale) {
            val saved = savedViewport()?.scale ?: state.entry.scale
            mutableFloatStateOf(
                saved?.coerceIn(fitScale, maxScale) ?: fitScale,
            )
        }
        var offset by remember(state.entry.id, fitScale) {
            val saved = savedViewport()
            mutableStateOf(
                centredOffset(
                    scale = scale,
                    centerX = saved?.centerX ?: state.entry.centerX
                        ?: (pageWidth / 2f),
                    centerY = saved?.centerY ?: state.entry.centerY
                        ?: (pageHeight / 2f),
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                ),
            )
        }

        // Bumped whenever a tile finishes, to trigger a redraw.
        var renderedCount by remember { mutableIntStateOf(0) }

        val scope = rememberCoroutineScope()
        var zoomAnimation by remember { mutableStateOf<Job?>(null) }

        /** Re-clamps the offset after any zoom or pan. */
        fun applyTransform(newScale: Float, newOffset: Offset) {
            scale = newScale.coerceIn(fitScale, maxScale)
            offset = clampOffset(
                offset = newOffset,
                scale = scale,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
            )
            onViewportChanged(
                viewportOf(scale, offset, viewWidth, viewHeight),
            )
        }

        /**
         * Eases to a zoom level while holding [anchor] still, so
         * the point under the finger stays under the finger.
         */
        fun animateZoomTo(target: Float, anchor: Offset) {
            zoomAnimation?.cancel()
            zoomAnimation = scope.launch {
                val fromScale = scale
                val fromOffset = offset
                val toScale = target.coerceIn(fitScale, maxScale)
                val factor = toScale / fromScale
                val toOffset = clampOffset(
                    offset = (fromOffset - anchor) * factor + anchor,
                    scale = toScale,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                )
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = ZOOM_ANIMATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                ) { fraction, _ ->
                    scale = fromScale + (toScale - fromScale) * fraction
                    offset = Offset(
                        fromOffset.x + (toOffset.x - fromOffset.x) * fraction,
                        fromOffset.y + (toOffset.y - fromOffset.y) * fraction,
                    )
                    onViewportChanged(
                        viewportOf(scale, offset, viewWidth, viewHeight),
                    )
                }
            }
        }

        val level = renderer.levelFor(scale)
        val visible = visibleRegion(
            offset = offset,
            scale = scale,
            levelScale = renderer.scaleForLevel(level),
            viewWidth = viewWidth,
            viewHeight = viewHeight,
        )
        val tiles = renderer.tilesFor(
            level = level,
            visible = visible,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
        val baseTiles = renderer.tilesFor(
            level = 0,
            visible = visibleRegion(
                offset = offset,
                scale = scale,
                levelScale = renderer.scaleForLevel(0),
                viewWidth = viewWidth,
                viewHeight = viewHeight,
            ),
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
        // One level coarser, drawn only where it is already cached
        // from zooming in. Level 0 alone is magnified up to 24x
        // while sharp tiles render, which reads as a smear rather
        // than a soft preview. Level 1's coarser level is level 0,
        // which is already drawn.
        val midTiles = if (level > 1) {
            renderer.tilesFor(
                level = level - 1,
                visible = visibleRegion(
                    offset = offset,
                    scale = scale,
                    levelScale = renderer.scaleForLevel(level - 1),
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                ),
                pageWidth = pageWidth,
                pageHeight = pageHeight,
            )
        } else {
            emptyList()
        }

        LaunchedEffect(tiles, baseTiles) {
            // Level 0 first: it is the underlay that stops the
            // screen going blank while sharp tiles are rendering.
            for (key in baseTiles + tiles) {
                if (renderer.render(key, pageWidth, pageHeight)) {
                    renderedCount++
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(fitScale) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            animateZoomTo(
                                nextZoomStop(scale, fitScale),
                                tap,
                            )
                        },
                    )
                }
                .pointerInput(fitScale) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // A finger on the screen wins over an
                        // in-flight double-tap animation.
                        zoomAnimation?.cancel()
                        val target =
                            (scale * zoom).coerceIn(fitScale, maxScale)
                        val factor = target / scale
                        applyTransform(
                            target,
                            (offset - centroid) * factor + centroid + pan,
                        )
                    }
                },
        ) {
            // Read so the canvas redraws as tiles arrive.
            @Suppress("UNUSED_EXPRESSION")
            renderedCount

            drawRect(Color.White)

            // Back to front. midTiles shows only where the sharp
            // tile has not landed yet; uncached keys are skipped.
            for (key in baseTiles + midTiles + tiles) {
                val bitmap = renderer.cached(key) ?: continue
                val bounds =
                    renderer.boundsOf(key, pageWidth, pageHeight)
                val levelScale = renderer.scaleForLevel(key.level)
                val left = bounds.left / levelScale * scale + offset.x
                val top = bounds.top / levelScale * scale + offset.y
                val width = bounds.width() / levelScale * scale
                val height = bounds.height() / levelScale * scale

                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(
                        left.roundToInt(),
                        top.roundToInt(),
                    ),
                    dstSize = IntSize(
                        width.roundToInt().coerceAtLeast(1),
                        height.roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}

/**
 * Describes the current view as a zoom and a page point, which is
 * the form that survives a change of viewport size.
 *
 * @param scale Current absolute scale.
 * @param offset Current pixel translation.
 * @param viewWidth Viewport width in pixels.
 * @param viewHeight Viewport height in pixels.
 * @return The page point at the centre of the viewport, with zoom.
 */
private fun viewportOf(
    scale: Float,
    offset: Offset,
    viewWidth: Float,
    viewHeight: Float,
): Viewport = Viewport(
    scale = scale,
    centerX = (viewWidth / 2f - offset.x) / scale,
    centerY = (viewHeight / 2f - offset.y) / scale,
)

/**
 * The zoom a double tap should move to next.
 *
 * Taps walk up through [DOUBLE_TAP_STOPS] and then back to fit, so
 * repeated tapping cycles fit to 3x to 6x and around again.
 *
 * @param scale Current absolute scale.
 * @param fitScale Scale at which the page fits the viewport.
 * @return The absolute scale to animate to.
 */
internal fun nextZoomStop(scale: Float, fitScale: Float): Float {
    val next = DOUBLE_TAP_STOPS.firstOrNull { stop ->
        scale < fitScale * stop / STOP_TOLERANCE
    }
    return fitScale * (next ?: 1f)
}

/**
 * The visible part of the page, in a level's scaled coordinates.
 *
 * @param offset Current pixel translation.
 * @param scale Current absolute scale.
 * @param levelScale Scale the tile level is rendered at.
 * @param viewWidth Viewport width in pixels.
 * @param viewHeight Viewport height in pixels.
 * @return The visible rectangle in scaled page coordinates.
 */
private fun visibleRegion(
    offset: Offset,
    scale: Float,
    levelScale: Float,
    viewWidth: Float,
    viewHeight: Float,
): Rect {
    val ratio = levelScale / scale
    return Rect(
        ((-offset.x) * ratio).toInt(),
        ((-offset.y) * ratio).toInt(),
        ((viewWidth - offset.x) * ratio).toInt(),
        ((viewHeight - offset.y) * ratio).toInt(),
    )
}

/**
 * Keeps the page inside the viewport, centring each axis on which
 * the page is smaller than the screen.
 *
 * @param offset Proposed translation.
 * @param scale Current absolute scale.
 * @param viewWidth Viewport width in pixels.
 * @param viewHeight Viewport height in pixels.
 * @param pageWidth Page width in points.
 * @param pageHeight Page height in points.
 * @return A translation that never shows the page flung off screen.
 */
private fun clampOffset(
    offset: Offset,
    scale: Float,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Int,
    pageHeight: Int,
): Offset {
    val scaledWidth = pageWidth * scale
    val scaledHeight = pageHeight * scale

    val x =
        if (scaledWidth <= viewWidth) (viewWidth - scaledWidth) / 2f
        else offset.x.coerceIn(viewWidth - scaledWidth, 0f)
    val y =
        if (scaledHeight <= viewHeight) (viewHeight - scaledHeight) / 2f
        else offset.y.coerceIn(viewHeight - scaledHeight, 0f)

    return Offset(x, y)
}

/**
 * The translation that puts a page point at the viewport centre.
 *
 * @param scale Current absolute scale.
 * @param centerX Page X to centre on.
 * @param centerY Page Y to centre on.
 * @param viewWidth Viewport width in pixels.
 * @param viewHeight Viewport height in pixels.
 * @param pageWidth Page width in points.
 * @param pageHeight Page height in points.
 * @return A clamped translation.
 */
private fun centredOffset(
    scale: Float,
    centerX: Float,
    centerY: Float,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Int,
    pageHeight: Int,
): Offset = clampOffset(
    offset = Offset(
        viewWidth / 2f - centerX * scale,
        viewHeight / 2f - centerY * scale,
    ),
    scale = scale,
    viewWidth = viewWidth,
    viewHeight = viewHeight,
    pageWidth = pageWidth,
    pageHeight = pageHeight,
)
