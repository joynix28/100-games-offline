package com.botillon.games.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Typo.body,
    color: Color = Ink.Text,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    softWrap: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align),
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    icon: (DrawScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "press")
    Box(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else Ink.Line)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Canvas(Modifier.size(18.dp)) { icon() }
                Spacer(Modifier.width(8.dp))
            }
            AppText(label, style = Typo.button, color = if (enabled) textColor else Ink.Muted)
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (DrawScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.CardAlt)
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Canvas(Modifier.size(16.dp)) { icon() }
                Spacer(Modifier.width(7.dp))
            }
            AppText(label, style = Typo.button.copy(fontSize = 13.sp), color = Ink.Soft)
        }
    }
}

@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    background: Color = Ink.Card,
    icon: DrawScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Ink.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.46f)) { icon() }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Ink.Card,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .border(1.dp, Ink.Line, RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Ink.Soft, background: Color = Ink.CardAlt) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        AppText(text, style = Typo.label, color = color)
    }
}

@Composable
fun BestPill(best: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(13.dp)) { drawTrophy(center, size.minDimension, Ink.Mustard) }
        Spacer(Modifier.width(5.dp))
        AppText("$best", style = Typo.small, color = Ink.Text)
    }
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            // Trame de points discrète, comme une page de cahier.
            val step = 26f
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(Ink.Line.copy(alpha = 0.55f), radius = 1.1f, center = androidx.compose.ui.geometry.Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}

