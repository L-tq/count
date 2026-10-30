package com.ltq.count.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ltq.count.R
import com.ltq.count.domain.heatmap.HeatCell
import com.ltq.count.domain.heatmap.HeatmapData
import com.ltq.count.domain.heatmap.HeatmapGranularity
import com.ltq.count.domain.heatmap.HeatmapMetric
import com.ltq.count.ui.components.Pill
import com.ltq.count.ui.components.SectionLabel
import com.ltq.count.ui.components.SoftCard
import com.ltq.count.ui.theme.rememberReducedMotion
import com.ltq.count.ui.util.CountFormat
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeatmapCard(
    heatmap: HeatmapData,
    modifier: Modifier = Modifier,
    onMetricChange: (HeatmapMetric) -> Unit,
    onGranularityChange: (HeatmapGranularity) -> Unit,
) {
    val locale = Locale.getDefault()
    val summary = if (heatmap.granularity.isIntraday) {
        stringResource(
            R.string.detail_heatmap_range,
            CountFormat.bucketDateTime(heatmap.rangeStart, locale),
            CountFormat.bucketDateTime(heatmap.rangeEnd, locale),
        )
    } else {
        stringResource(
            R.string.detail_heatmap_range,
            CountFormat.date(heatmap.rangeStart.toLocalDate(), locale),
            CountFormat.date(heatmap.rangeEnd.toLocalDate(), locale),
        )
    }
    SoftCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.detail_heatmap_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(14.dp))
        SectionLabel(text = stringResource(R.string.detail_heatmap_metric))
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                label = stringResource(R.string.detail_heatmap_metric_count),
                selected = heatmap.metric == HeatmapMetric.EVENT_COUNT,
                onClick = { onMetricChange(HeatmapMetric.EVENT_COUNT) },
            )
            Pill(
                label = stringResource(R.string.detail_heatmap_metric_abs),
                selected = heatmap.metric == HeatmapMetric.ABS_DELTA,
                onClick = { onMetricChange(HeatmapMetric.ABS_DELTA) },
            )
            Pill(
                label = stringResource(R.string.detail_heatmap_metric_net),
                selected = heatmap.metric == HeatmapMetric.NET_DELTA,
                onClick = { onMetricChange(HeatmapMetric.NET_DELTA) },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel(text = stringResource(R.string.detail_heatmap_granularity))
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                label = stringResource(R.string.detail_heatmap_minute),
                selected = heatmap.granularity == HeatmapGranularity.MINUTE,
                onClick = { onGranularityChange(HeatmapGranularity.MINUTE) },
            )
            Pill(
                label = stringResource(R.string.detail_heatmap_hour),
                selected = heatmap.granularity == HeatmapGranularity.HOUR,
                onClick = { onGranularityChange(HeatmapGranularity.HOUR) },
            )
            Pill(
                label = stringResource(R.string.detail_heatmap_day),
                selected = heatmap.granularity == HeatmapGranularity.DAY,
                onClick = { onGranularityChange(HeatmapGranularity.DAY) },
            )
            Pill(
                label = stringResource(R.string.detail_heatmap_week),
                selected = heatmap.granularity == HeatmapGranularity.WEEK,
                onClick = { onGranularityChange(HeatmapGranularity.WEEK) },
            )
            Pill(
                label = stringResource(R.string.detail_heatmap_month),
                selected = heatmap.granularity == HeatmapGranularity.MONTH,
                onClick = { onGranularityChange(HeatmapGranularity.MONTH) },
            )
        }

        Spacer(Modifier.height(18.dp))
        when (heatmap.granularity) {
            HeatmapGranularity.DAY -> DayHeatmap(heatmap)
            HeatmapGranularity.MINUTE,
            HeatmapGranularity.HOUR,
            HeatmapGranularity.WEEK,
            HeatmapGranularity.MONTH -> BucketBars(heatmap)
        }
        Spacer(Modifier.height(10.dp))
        if (heatmap.isEmpty) {
            Text(
                text = stringResource(R.string.detail_heatmap_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        HeatLegend(hasNegative = heatmap.metric == HeatmapMetric.NET_DELTA && heatmap.hasNegative)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.detail_heatmap_summary, heatmap.totalEvents),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeatmap(heatmap: HeatmapData) {
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val locale = Locale.getDefault()
    val textMeasurer = rememberTextMeasurer()

    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    val cellSize = 10.dp
    val gap = 3.dp
    val gutter = 18.dp
    val labelHeight = 15.dp
    val weekCount = heatmap.weeks.size
    val width = gutter + (cellSize + gap) * weekCount
    val height = labelHeight + (cellSize + gap) * 7
    val scrollState = rememberScrollState()

    LaunchedEffect(heatmap.metric, heatmap.granularity, heatmap.totalEvents) {
        if (!reducedMotion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 600))
        }
    }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }

    val totalCells = weekCount * 7
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Canvas(modifier = Modifier.width(width).height(height)) {
            val cellPx = cellSize.toPx()
            val gapPx = gap.toPx()
            val stepPx = cellPx + gapPx
            val gutterPx = gutter.toPx()
            val labelHeightPx = labelHeight.toPx()
            val t = progress.value
            val fadeWindow = 22f

            heatmap.monthLabels.forEach { label ->
                val text = CountFormat.monthShort(label.month, locale)
                val layout = textMeasurer.measure(AnnotatedString(text), style = labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(gutterPx + label.weekIndex * stepPx, 0f),
                )
            }

            val weekdayRows = listOf(
                0 to DayOfWeek.MONDAY,
                2 to DayOfWeek.WEDNESDAY,
                4 to DayOfWeek.FRIDAY,
            )
            weekdayRows.forEach { (row, day) ->
                val text = CountFormat.weekdayNarrow(day, locale)
                val layout = textMeasurer.measure(AnnotatedString(text), style = labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        gutterPx - layout.size.width - 4.dp.toPx(),
                        labelHeightPx + row * stepPx + (cellPx - layout.size.height) / 2f,
                    ),
                )
            }

            for (weekIndex in 0 until weekCount) {
                val column = heatmap.weeks[weekIndex]
                for (row in 0 until 7) {
                    val index = weekIndex * 7 + row
                    val alpha = if (reducedMotion) {
                        1f
                    } else {
                        ((t * (totalCells + fadeWindow) - index) / fadeWindow).coerceIn(0f, 1f)
                    }
                    val cell = column.getOrNull(row)
                    if (cell == null) continue
                    val color = cellColor(cell, emptyColor, primary, negative, cardColor)
                    drawRoundRect(
                        color = color.copy(alpha = color.alpha * alpha),
                        topLeft = Offset(gutterPx + weekIndex * stepPx, labelHeightPx + row * stepPx),
                        size = Size(cellPx, cellPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun BucketBars(heatmap: HeatmapData) {
    val locale = Locale.getDefault()
    val max = heatmap.maxMagnitude.coerceAtLeast(1L)
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(heatmap.metric, heatmap.granularity, heatmap.totalEvents) {
        if (!reducedMotion) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 520))
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(if (heatmap.cells.size > 30) 1.dp else 3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            heatmap.cells.forEach { cell ->
                val fraction = if (cell.value == 0L) 0f else (abs(cell.value).toFloat() / max).coerceIn(0f, 1f)
                val barColor = if (cell.negative) negative else primary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (cell.value == 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(2.dp)
                                .background(emptyColor, RoundedCornerShape(1.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight((fraction * progress.value).coerceIn(0.04f, 1f))
                                .background(
                                    barColor,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (heatmap.granularity == HeatmapGranularity.MONTH) {
                Arrangement.SpaceEvenly
            } else {
                Arrangement.SpaceBetween
            },
        ) {
            if (heatmap.granularity == HeatmapGranularity.MONTH) {
                heatmap.cells.forEach { cell ->
                    Text(
                        text = CountFormat.monthShort(cell.key.monthValue, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else if (heatmap.granularity.isIntraday) {
                val first = heatmap.cells.first().key
                val middle = heatmap.cells[heatmap.cells.size / 2].key
                val last = heatmap.cells.last().key
                listOf(first, middle, last).forEach { time ->
                    Text(
                        text = CountFormat.bucketClock(time, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                val first = heatmap.cells.first().key
                val middle = heatmap.cells[heatmap.cells.size / 2].key
                val last = heatmap.cells.last().key
                listOf(first, middle, last).forEach { bucket ->
                    Text(
                        text = CountFormat.monthDay(bucket.toLocalDate(), locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatLegend(hasNegative: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val negative = MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.detail_heatmap_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(empty, RoundedCornerShape(2.dp)),
        )
        (1..4).forEach { level ->
            Spacer(Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        lerp(empty, primary, 0.14f + 0.2f * level),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.detail_heatmap_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasNegative) {
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(lerp(empty, negative, 0.7f), RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.detail_heatmap_negative),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun cellColor(
    cell: HeatCell,
    emptyColor: Color,
    primary: Color,
    negative: Color,
    cardColor: Color,
): Color {
    if (cell.level == 0) return emptyColor
    val target = if (cell.negative) negative else primary
    return lerp(cardColor, target, 0.14f + 0.2f * cell.level)
}
