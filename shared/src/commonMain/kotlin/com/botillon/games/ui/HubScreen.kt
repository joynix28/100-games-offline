package com.botillon.games.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HubScreen(
    entries: List<GameEntry>,
    progress: Progress,
    onPlay: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var filter by remember { mutableStateOf<Family?>(null) }
    val visible = entries.filter { filter == null || it.family == filter }
    val variants = entries.sumOf { it.variants.size }

    Backdrop {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppText("SANS PUB · HORS-LIGNE", style = Typo.label, color = Ink.Coral)
                        Spacer(Modifier.height(2.dp))
                        Row {
                            AppText("100 ", style = Typo.display, color = Ink.Text)
                            AppText("Games", style = Typo.display, color = Ink.Teal)
                        }
                        AppText(
                            "${entries.size} jeux · $variants variantes · aucun compte",
                            style = Typo.small,
                            color = Ink.Muted,
                        )
                    }
                    CircleButton(onClick = onSettings) {
                        drawGear(center, size.minDimension, Ink.Soft)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilterChip("Tout", filter == null, Ink.Text) { filter = null }
                    Family.entries.forEach { family ->
                        FilterChip(family.label, filter == family, Ink.Teal) { filter = family }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.id }) { entry ->
                    GameCard(
                        entry = entry,
                        best = progress.bestOf(entry.id),
                        played = entry.id in progress.played,
                        onClick = { onPlay(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Ink.Card)
            .border(1.dp, if (selected) accent else Ink.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AppText(label, style = Typo.small, color = if (selected) Color.White else Ink.Soft)
    }
}

@Composable
private fun GameCard(entry: GameEntry, best: Long, played: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(entry.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                    drawFamilyGlyph(entry, Ink.Accents.indexOf(entry.accent).coerceAtLeast(0), this)
                }
            }
            Spacer(Modifier.height(10.dp))
            AppText(entry.title, style = Typo.heading, color = Ink.Text, maxLines = 2, softWrap = true)
            Spacer(Modifier.height(2.dp))
            AppText(entry.tagline, style = Typo.small, color = Ink.Muted, maxLines = 2, softWrap = true)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.variants.size > 1) {
                    Pill("${entry.variants.size} variantes", color = Ink.Muted, background = Ink.CardAlt)
                }
                Spacer(Modifier.weight(1f))
                if (best > 0) {
                    AppText("$best", style = Typo.small, color = entry.accent)
                } else if (!played) {
                    AppText("—", style = Typo.small, color = Ink.Line)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(entry.accent),
        )
    }
}

private fun drawFamilyGlyph(entry: GameEntry, index: Int, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    with(scope) {
        val c = center
        val s = size.minDimension
        when (entry.family) {
            Family.BOARD -> {
                for (y in 0..1) for (x in 0..1) {
                    drawRoundRect(
                        color = entry.accent,
                        topLeft = Offset(c.x - s * 0.4f + x * s * 0.46f, c.y - s * 0.4f + y * s * 0.46f),
                        size = Size(s * 0.36f, s * 0.36f),
                        cornerRadius = CornerRadius(s * 0.08f),
                    )
                }
            }

            Family.LOGIC -> {
                drawCircle(entry.accent, radius = s * 0.42f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(s * 0.16f))
                drawCircle(entry.accent, radius = s * 0.14f, center = c)
            }

            Family.CARD -> {
                for (i in 0..1) {
                    drawRoundRect(
                        color = entry.accent.copy(alpha = 0.55f + 0.45f * i),
                        topLeft = Offset(c.x - s * 0.34f + i * s * 0.16f, c.y - s * 0.40f + i * s * 0.10f),
                        size = Size(s * 0.52f, s * 0.72f),
                        cornerRadius = CornerRadius(s * 0.10f),
                    )
                }
            }

            Family.ARCADE -> {
                drawRoundRect(
                    color = entry.accent,
                    topLeft = Offset(c.x - s * 0.42f, c.y - s * 0.20f),
                    size = Size(s * 0.84f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.16f),
                )
                drawCircle(Color.White.copy(alpha = 0.9f), radius = s * 0.07f, center = Offset(c.x - s * 0.18f, c.y + s * 0.02f))
                drawCircle(Color.White.copy(alpha = 0.9f), radius = s * 0.07f, center = Offset(c.x + s * 0.18f, c.y + s * 0.02f))
            }

            Family.WORDS -> {
                for (i in 0..2) {
                    drawRoundRect(
                        color = entry.accent.copy(alpha = 1f - i * 0.22f),
                        topLeft = Offset(c.x - s * 0.40f, c.y - s * 0.34f + i * s * 0.26f),
                        size = Size(s * (0.80f - i * 0.16f), s * 0.14f),
                        cornerRadius = CornerRadius(s * 0.07f),
                    )
                }
            }
        }
    }
}
