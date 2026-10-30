package com.ltq.count.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ltq.count.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

/**
 * The single signature animation of the app: on every value change the number slides up a few
 * dp while fading in, paired with a very short haptic tick. Tabular figures keep the width
 * stable. When the system removes animations, only the value swaps.
 *
 * The font size is fitted to the available width so that nine- and ten-digit values never
 * overflow their card or the fullscreen counter.
 */
@Composable
fun AnimatedValueText(
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(1f) }
    var lastValue by remember { mutableLongStateOf(value) }
    val fontScale = LocalDensity.current.fontScale

    LaunchedEffect(value) {
        if (value != lastValue) {
            lastValue = value
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (!reducedMotion) {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    val p = progress.value
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val baseSize = style.fontSize.value
        val chars = value.toString().length.toFloat()
        val available = maxWidth.value
        val fitted = if (chars > 0f && available.isFinite() && baseSize > 0f) {
            // Serif tabular digits are roughly 0.62 em wide; 24sp is the legibility floor.
            (available / (chars * 0.62f * fontScale))
                .coerceAtMost(baseSize)
                .coerceAtLeast(24f)
        } else {
            baseSize
        }
        val scale = if (baseSize > 0f) fitted / baseSize else 1f
        val lineHeight: TextUnit = if (style.lineHeight != TextUnit.Unspecified) {
            (style.lineHeight.value * scale).sp
        } else {
            TextUnit.Unspecified
        }
        val semantics = if (contentDescription != null) {
            Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
        Text(
            text = value.toString(),
            style = style.copy(fontSize = fitted.sp, lineHeight = lineHeight),
            color = color,
            modifier = Modifier
                .offset { IntOffset(0, ((1f - p) * 6f).roundToInt()) }
                .graphicsLayer { alpha = 0.3f + 0.7f * p }
                .then(semantics),
            maxLines = 1,
        )
    }
}
