package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.ui.components.TvActionButton
import com.fongmi.android.tv.ui.theme.JetStreamFontFamily
import com.fongmi.android.tv.ui.theme.JetStreamTheme

class JetStreamEpisodeGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    interface OnChipClickListener {
        fun onChipClick(position: Int)
    }

    private val items = mutableStateListOf<String>()
    private var selectedIndex by mutableIntStateOf(-1)
    private var focusedIndex by mutableIntStateOf(-1)
    private var entryFocusToken by mutableStateOf(0L)
    private var focusGeneration by mutableIntStateOf(0)
    private var pendingFocusIndex by mutableIntStateOf(-1)
    private var actualFocusedIndex by mutableIntStateOf(-1)
    private var windowFocused by mutableStateOf(false)
    private var packedRows: List<JetStreamEpisodeGridPolicy.Row> = emptyList()
    private var clickListener: ((Int) -> Unit)? = null

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setItems(texts: List<String>, selected: Int) {
        if (items.toList() == texts) {
            setSelectedPosition(selected)
            return
        }
        val previousFocus = focusedIndex
        items.clear()
        items.addAll(texts)
        selectedIndex = if (selected in items.indices) selected else -1
        focusedIndex = when {
            items.isEmpty() -> -1
            selectedIndex in items.indices -> selectedIndex
            else -> 0
        }
        pendingFocusIndex = -1
        actualFocusedIndex = -1
        if (focusedIndex != previousFocus || hasFocus()) entryFocusToken++
    }

    fun setSelectedPosition(position: Int) {
        selectedIndex = if (position in items.indices) position else -1
        if (selectedIndex >= 0 && !hasFocus()) {
            focusedIndex = selectedIndex
            pendingFocusIndex = -1
            entryFocusToken++
        }
    }

    fun setFocusedPosition(position: Int) {
        val target = JetStreamEpisodeGridPolicy.clampIndex(position, items.size)
        if (target < 0) return
        if (getFocusedPosition() == target) return
        val generation = ++focusGeneration
        val previous = rootView.findFocus()
        focusedIndex = target
        pendingFocusIndex = target
        actualFocusedIndex = -1
        entryFocusToken++
        if (!hasFocus()) {
            post {
                if (generation == focusGeneration && isShown && isEnabled && rootView.findFocus() === previous) requestFocus()
            }
        }
    }

    fun getSelectedPosition(): Int = selectedIndex

    fun getFocusedPosition(): Int = if (hasFocus()) actualFocusedIndex else -1

    fun setOnChipClickListener(listener: OnChipClickListener) {
        clickListener = { position -> listener.onChipClick(position) }
    }

    fun clearListeners() {
        clickListener = null
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            normalizeFocus()
            entryFocusToken++
        } else {
            focusGeneration++
            actualFocusedIndex = -1
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        windowFocused = hasWindowFocus
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        windowFocused = hasWindowFocus()
    }

    private fun normalizeFocus() {
        if (items.isEmpty()) {
            focusedIndex = -1
            return
        }
        if (focusedIndex in items.indices) return
        focusedIndex = if (selectedIndex in items.indices) selectedIndex else 0
    }

    private fun clickChip(index: Int) {
        if (index in items.indices) clickListener?.invoke(index)
    }

    private fun moveViewFocus(direction: Int): Boolean {
        val targetId = if (direction == View.FOCUS_UP) nextFocusUpId else nextFocusDownId
        val explicitTarget = if (targetId != View.NO_ID) rootView.findViewById<View>(targetId) else null
        val next = explicitTarget ?: focusSearch(direction)
        return next != null && next !== this && next.isShown && next.isEnabled && next.requestFocus()
    }

    private fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (focusedIndex !in items.indices) return false
        return when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (JetStreamEpisodeGridPolicy.handoffUp(packedRows, focusedIndex)) moveViewFocus(View.FOCUS_UP) else false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (JetStreamEpisodeGridPolicy.handoffDown(packedRows, focusedIndex)) moveViewFocus(View.FOCUS_DOWN) else false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> JetStreamEpisodeGridPolicy.atRowStart(packedRows, focusedIndex)
            KeyEvent.KEYCODE_DPAD_RIGHT -> JetStreamEpisodeGridPolicy.atRowEnd(packedRows, focusedIndex)
            else -> false
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        JetStreamTheme {
            val gridState = rememberLazyListState()
            val snapshot = items.toList()
            val requesters = remember(snapshot) { List(snapshot.size) { FocusRequester() } }
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val textStyle = TextStyle(
                fontFamily = JetStreamFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                letterSpacing = 0.1.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
            val titles = remember(snapshot) {
                snapshot.map { title -> title.replace('\n', ' ').replace('\r', ' ') }
            }
            val widths = remember(titles, density.density, density.fontScale, measurer) {
                val padding = with(density) { 20.dp.roundToPx() }
                val minimum = with(density) { 48.dp.roundToPx() }
                titles.map { title ->
                    val normal = measurer.measure(title, style = textStyle, softWrap = false, maxLines = 1)
                    val selected = measurer.measure(
                        title, style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                        softWrap = false, maxLines = 1
                    )
                    (maxOf(normal.size.width, selected.size.width) + padding + 2).coerceAtLeast(minimum)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val rowWidth = with(density) { (maxWidth - 12.dp).roundToPx() }.coerceAtLeast(1)
                val gap = with(density) { SPACING_DP.dp.roundToPx() }
                val rows = remember(widths, rowWidth, gap) {
                    JetStreamEpisodeGridPolicy.layout(widths, rowWidth, gap)
                }
                packedRows = rows
                val heightDp = JetStreamEpisodeGridPolicy.gridHeightDp(
                    JetStreamEpisodeGridPolicy.visibleRows(rows),
                    CELL_HEIGHT_DP, SPACING_DP, VERTICAL_PADDING_DP
                )
                LaunchedEffect(entryFocusToken, rows) {
                    val token = entryFocusToken
                    val generation = focusGeneration
                    val target = focusedIndex
                    val row = JetStreamEpisodeGridPolicy.rowFor(rows, target)
                    if (token <= 0L || row < 0) return@LaunchedEffect
                    gridState.scrollToItem(row)
                    repeat(8) {
                        withFrameNanos { }
                        if (token != entryFocusToken || generation != focusGeneration) return@LaunchedEffect
                        if (!hasFocus()) return@LaunchedEffect
                        runCatching { requesters[target].requestFocus() }
                        if (actualFocusedIndex == target) return@LaunchedEffect
                    }
                    android.util.Log.w("CinemaEpisodeGrid", "Episode focus was not ready after layout")
                }
                LaunchedEffect(focusedIndex, rows) {
                    val row = JetStreamEpisodeGridPolicy.rowFor(rows, focusedIndex)
                    if (row >= 0 && gridState.layoutInfo.visibleItemsInfo.none { itemInfo -> itemInfo.index == row }) {
                        gridState.scrollToItem(row)
                    }
                }
                if (snapshot.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(heightDp.dp).clipToBounds()) {
                        LazyColumn(
                            state = gridState,
                            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                                handleKey(event)
                            },
                            contentPadding = PaddingValues(
                                horizontal = 6.dp,
                                vertical = VERTICAL_PADDING_DP.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(SPACING_DP.dp)
                        ) {
                            itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(SPACING_DP.dp)) {
                                    row.indices.forEachIndexed { column, index ->
                                        var chipFocused by remember(index) { mutableStateOf(false) }
                                        TvActionButton(
                                            onClick = { clickChip(index) },
                                            selected = index == selectedIndex,
                                            focusedScale = 1f,
                                            modifier = Modifier.width(with(density) { row.widths[column].toDp() })
                                                .height(CELL_HEIGHT_DP.dp)
                                                .focusRequester(requesters[index])
                                                .onFocusChanged { state ->
                                                    chipFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        focusedIndex = index
                                                        pendingFocusIndex = -1
                                                        actualFocusedIndex = index
                                                    } else if (actualFocusedIndex == index) {
                                                        actualFocusedIndex = -1
                                                    }
                                                },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = titles[index],
                                                modifier = if (chipFocused && windowFocused && widths[index] > rowWidth) {
                                                    Modifier.basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        animationMode = MarqueeAnimationMode.Immediately
                                                    )
                                                } else Modifier,
                                                fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                style = textStyle
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        private const val CELL_HEIGHT_DP = 48
        private const val SPACING_DP = 8
        private const val VERTICAL_PADDING_DP = 4
    }
}
