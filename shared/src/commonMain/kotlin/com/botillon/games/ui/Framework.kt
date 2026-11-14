package com.botillon.games.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Famille de jeu, pour l'icône et le tri dans le hub. */
enum class Family(val label: String) {
    BOARD("Plateau"),
    LOGIC("Logique"),
    CARD("Cartes"),
    ARCADE("Arcade"),
    WORDS("Mots & nombres"),
}

/**
 * Un jeu du catalogue. Chaque jeu gère son propre état et signale la fin d'une
 * partie via [content] ; le cadre commun s'occupe du titre, des variantes et
 * du panneau de fin.
 */
data class GameEntry(
    val id: String,
    val title: String,
    val tagline: String,
    val family: Family,
    val accent: Color,
    val variants: List<String>,
    val content: @Composable (
        variant: Int,
        best: Long,
        finish: (score: Long, won: Boolean) -> Unit,
        onExit: () -> Unit,
    ) -> Unit,
)

@Composable
fun GameHost(
    entry: GameEntry,
    best: Long,
    onResult: (score: Long) -> Unit,
    onExit: () -> Unit,
) {
    var variant by remember(entry.id) { mutableIntStateOf(0) }
    var runId by remember(entry.id) { mutableIntStateOf(0) }
    var result by remember(entry.id) { mutableStateOf<Pair<Long, Boolean>?>(null) }

    Backdrop {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton(onClick = onExit) {
                    drawChevronLeft(center, size.minDimension, Ink.Soft)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    AppText(entry.title, style = Typo.title, color = Ink.Text)
                    AppText(entry.tagline, style = Typo.small, color = Ink.Muted)
                }
                BestPill(best)
            }

            if (entry.variants.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    entry.variants.forEachIndexed { index, label ->
                        VariantChip(
                            label = label,
                            selected = index == variant,
                            accent = entry.accent,
                            onClick = {
                                variant = index
                                result = null
                                runId++
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(runId, variant) {
                    entry.content(
                        variant,
                        best,
                        { score, won ->
                            if (result == null) {
                                result = score to won
                                onResult(score)
                            }
                        },
                        onExit,
                    )
                }
            }
        }

        result?.let { (score, won) ->
            FinishPanel(
                entry = entry,
                score = score,
                won = won,
                best = best,
                onReplay = {
                    result = null
                    runId++
                },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun VariantChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Ink.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AppText(
            text = label,
            style = Typo.small,
            color = if (selected) Color.White else Ink.Soft,
        )
    }
}

@Composable
fun FinishPanel(
    entry: GameEntry,
    score: Long,
    won: Boolean,
    best: Long,
    onReplay: () -> Unit,
    onExit: () -> Unit,
) {
    val strings = LocalStrings.current
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "fade")
    LaunchedEffect(Unit) { shown = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg.copy(alpha = 0.84f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(
                    text = if (won) strings.won else strings.over,
                    style = Typo.label.copy(letterSpacing = 2.sp),
                    color = if (won) Ink.Teal else Ink.Muted,
                )
                Spacer(Modifier.height(6.dp))
                AppText(entry.title, style = Typo.heading, color = Ink.Text)
                Spacer(Modifier.height(10.dp))
                AppText("$score", style = Typo.big, color = Ink.Text)
                Spacer(Modifier.height(2.dp))
                AppText(
                    text = if (score >= best && best > 0) strings.newRecord else "${strings.record} : $best",
                    style = Typo.small,
                    color = if (score >= best && best > 0) Ink.Coral else Ink.Muted,
                )
                Spacer(Modifier.height(22.dp))
                PrimaryButton(strings.playAgain, onReplay, Modifier.fillMaxWidth(), color = entry.accent)
                Spacer(Modifier.height(9.dp))
                SecondaryButton(strings.menu, onExit, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Petit bandeau d'aide affiché sous un jeu. */
@Composable
fun HintBar(text: String, modifier: Modifier = Modifier, accent: Color = Ink.Teal) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        AppText(
            text,
            style = Typo.small,
            color = accent,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
    }
}
