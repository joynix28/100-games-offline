package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Dominos : le joueur et l'ordinateur posent à tour de rôle sur les deux
 * extrémités de la chaîne. On pioche quand rien ne va, on passe quand la
 * pioche est vide ; le moins de points en main gagne en cas de blocage.
 */
object DominoesGame {
    val entry = GameEntry(
        id = "dominos",
        title = "Dominos",
        tagline = "Poser ses dominos avant l'ordinateur",
        family = Family.BOARD,
        accent = Ink.Mustard,
        variants = listOf("Double-six", "Double-six long", "Double-neuf"),
    ) { variant, _, finish, _ ->
        Dominoes(variant, finish)
    }
}

private data class Domino(val a: Int, val b: Int)

private data class Deal(
    val player: List<Domino>,
    val machine: List<Domino>,
    val boneyard: List<Domino>,
)

/** Toutes les pièces d'un jeu double-[maxPip], dans l'ordre. */
private fun fullSet(maxPip: Int): List<Domino> = buildList {
    for (a in 0..maxPip) for (b in a..maxPip) add(Domino(a, b))
}

private fun total(tiles: List<Domino>): Int = tiles.sumOf { it.a + it.b }

@Composable
private fun Dominoes(variant: Int, finish: (Long, Boolean) -> Unit) {
    val maxPip = if (variant == 2) 9 else 6
    val handSize = if (variant == 0) 6 else 7
    val accent = Ink.Mustard
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    val deal = remember(variant) {
        val all = fullSet(maxPip).shuffled(Random.Default)
        Deal(
            player = all.take(handSize),
            machine = all.drop(handSize).take(handSize),
            boneyard = all.drop(handSize * 2),
        )
    }

    var hand by remember(variant) { mutableStateOf(deal.player) }
    var machineHand by remember(variant) { mutableStateOf(deal.machine) }
    var boneyard by remember(variant) { mutableStateOf(deal.boneyard) }
    var chain by remember(variant) { mutableStateOf<List<Domino>>(emptyList()) }
    var leftEnd by remember(variant) { mutableIntStateOf(-1) }
    var rightEnd by remember(variant) { mutableIntStateOf(-1) }
    var turn by remember(variant) { mutableIntStateOf(0) }
    var selected by remember(variant) { mutableStateOf<Domino?>(null) }
    var notice by remember(variant) { mutableStateOf("Touchez un domino pour commencer.") }
    var over by remember(variant) { mutableStateOf(false) }
    var reported by remember(variant) { mutableStateOf(false) }

    fun leftOk(tile: Domino): Boolean =
        chain.isEmpty() || tile.a == leftEnd || tile.b == leftEnd

    fun rightOk(tile: Domino): Boolean =
        chain.isEmpty() || tile.a == rightEnd || tile.b == rightEnd

    fun canPlay(tile: Domino): Boolean = leftOk(tile) || rightOk(tile)

    /** Termine la manche une seule fois, avec le barème demandé. */
    fun finishWith(won: Boolean, opponentPoints: Int) {
        if (reported) return
        reported = true
        over = true
        selected = null
        val score = if (won) 100L + opponentPoints * 5L else 0L
        notice = if (won) "Manche gagnée !" else "Manche perdue."
        if (won) {
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
        }
        finish(score, won)
    }

    fun endGame(won: Boolean) {
        finishWith(won, total(machineHand))
    }

    /** Personne ne peut plus jouer : le moins de points en main l'emporte. */
    fun endBlocked() {
        if (reported) return
        val mine = total(hand)
        val theirs = total(machineHand)
        finishWith(mine < theirs, theirs)
    }

    fun placePlayer(tile: Domino, side: Int) {
        if (over || turn != 0 || tile !in hand) return
        if (chain.isEmpty()) {
            chain = listOf(tile)
            leftEnd = tile.a
            rightEnd = tile.b
        } else if (side == 0) {
            if (!leftOk(tile)) return
            val oriented = if (tile.b == leftEnd) tile else Domino(tile.b, tile.a)
            chain = listOf(oriented) + chain
            leftEnd = oriented.a
        } else {
            if (!rightOk(tile)) return
            val oriented = if (tile.a == rightEnd) tile else Domino(tile.b, tile.a)
            chain = chain + oriented
            rightEnd = oriented.b
        }
        hand = hand.filter { it != tile }
        selected = null
        sound.play(Sound.TAP)
        haptics.tap()
        if (hand.isEmpty()) {
            endGame(true)
            return
        }
        notice = "À l'ordinateur de poser…"
        turn = 1
    }

    fun placeMachine(tile: Domino, side: Int) {
        if (over) return
        if (chain.isEmpty()) {
            chain = listOf(tile)
            leftEnd = tile.a
            rightEnd = tile.b
        } else if (side == 0) {
            if (!(tile.a == leftEnd || tile.b == leftEnd)) return
            val oriented = if (tile.b == leftEnd) tile else Domino(tile.b, tile.a)
            chain = listOf(oriented) + chain
            leftEnd = oriented.a
        } else {
            if (!(tile.a == rightEnd || tile.b == rightEnd)) return
            val oriented = if (tile.a == rightEnd) tile else Domino(tile.b, tile.a)
            chain = chain + oriented
            rightEnd = oriented.b
        }
        machineHand = machineHand.filter { it != tile }
        sound.play(Sound.TAP)
        haptics.tap()
        notice = "L'ordinateur pose ${tile.a} · ${tile.b}."
        if (machineHand.isEmpty()) {
            endGame(false)
            return
        }
        turn = 0
    }

    /** Coups possibles de l'ordinateur : la pièce et le côté (0 gauche, 1 droite). */
    fun aiChoices(): List<Pair<Domino, Int>> {
        if (chain.isEmpty()) return machineHand.map { it to 1 }
        val out = mutableListOf<Pair<Domino, Int>>()
        for (t in machineHand) {
            if (t.a == leftEnd || t.b == leftEnd) out.add(t to 0)
            if (t.a == rightEnd || t.b == rightEnd) out.add(t to 1)
        }
        return out
    }

    fun drawPlayer() {
        if (over || turn != 0 || boneyard.isEmpty() || hand.any { canPlay(it) }) return
        val tile = boneyard.first()
        hand = hand + tile
        boneyard = boneyard.drop(1)
        sound.play(Sound.FLIP)
        haptics.tap()
        notice = if (canPlay(tile)) {
            "Pioche : ${tile.a} · ${tile.b}. À vous de jouer."
        } else {
            "Pioche : ${tile.a} · ${tile.b}. Toujours rien…"
        }
    }

    fun playerPass() {
        if (over || turn != 0 || boneyard.isNotEmpty() || hand.any { canPlay(it) }) return
        selected = null
        notice = "Vous passez votre tour."
        turn = 1
    }

    fun onTile(tile: Domino) {
        if (over || turn != 0) return
        if (chain.isEmpty()) {
            placePlayer(tile, 1)
            return
        }
        val canLeft = leftOk(tile)
        val canRight = rightOk(tile)
        if (!canLeft && !canRight) {
            notice = "Ce domino ne correspond à aucune extrémité."
            sound.play(Sound.UI)
            haptics.error()
            return
        }
        if (canLeft && canRight) {
            if (selected == tile) {
                selected = null
                notice = "Touchez un domino de votre main."
            } else {
                selected = tile
                notice = "Choisissez une extrémité."
            }
            return
        }
        placePlayer(tile, if (canLeft) 0 else 1)
    }

    // Tour de l'ordinateur : il pioche tant qu'il ne peut pas jouer, puis pose.
    LaunchedEffect(turn, over) {
        if (turn != 1 || over) return@LaunchedEffect
        delay(520)
        var choices = aiChoices()
        while (choices.isEmpty() && boneyard.isNotEmpty() && !over) {
            machineHand = machineHand + boneyard.first()
            boneyard = boneyard.drop(1)
            sound.play(Sound.FLIP)
            delay(320)
            choices = aiChoices()
        }
        if (over) return@LaunchedEffect
        if (choices.isEmpty()) {
            if (boneyard.isEmpty() && hand.none { canPlay(it) }) {
                endBlocked()
            } else {
                notice = "L'ordinateur passe. À vous."
                turn = 0
            }
        } else {
            // Priorité au plus gros domino, hasard entre les ex æquo.
            val pick = choices.shuffled(Random.Default)
                .maxByOrNull { it.first.a + it.first.b }
                ?: choices.first()
            placeMachine(pick.first, pick.second)
        }
    }

    val playerHasMove = hand.any { canPlay(it) }
    val selectedTile = selected

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("Vous ${hand.size}", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.10f))
            Pill("Pioche ${boneyard.size}", color = Ink.Soft, background = Ink.CardAlt)
            Pill("IA ${machineHand.size}", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.10f))
            Spacer(Modifier.weight(1f))
            Pill(
                text = "Extrémités ${if (leftEnd < 0) "—" else "$leftEnd"} · ${if (rightEnd < 0) "—" else "$rightEnd"}",
                color = Ink.Text,
                background = accent.copy(alpha = 0.18f),
            )
        }

        Spacer(Modifier.height(6.dp))
        AppText(
            text = notice,
            style = Typo.small,
            color = if (over) Ink.Muted else Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth().weight(1f), color = Ink.CardAlt) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                drawChain(chain, accent)
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            when {
                over -> AppText("Manche terminée.", style = Typo.heading, color = Ink.Muted)
                turn == 1 -> AppText("L'ordinateur réfléchit…", style = Typo.body, color = Ink.Muted)
                selectedTile != null -> {
                    val tile = selectedTile
                    val canLeft = leftOk(tile)
                    val canRight = rightOk(tile)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            label = if (canLeft) "◀ Gauche ($leftEnd)" else "◀ Gauche",
                            onClick = { placePlayer(tile, 0) },
                            modifier = Modifier.weight(1f),
                            color = Ink.Soft,
                            enabled = canLeft,
                        )
                        PrimaryButton(
                            label = if (canRight) "Droite ($rightEnd) ▶" else "Droite ▶",
                            onClick = { placePlayer(tile, 1) },
                            modifier = Modifier.weight(1f),
                            color = accent,
                            enabled = canRight,
                        )
                    }
                }
                !playerHasMove -> {
                    if (boneyard.isNotEmpty()) {
                        PrimaryButton(
                            label = "Piocher (${boneyard.size})",
                            onClick = { drawPlayer() },
                            modifier = Modifier.fillMaxWidth(),
                            color = Ink.Teal,
                        )
                    } else {
                        PrimaryButton(
                            label = "Passer",
                            onClick = { playerPass() },
                            modifier = Modifier.fillMaxWidth(),
                            color = Ink.Soft,
                        )
                    }
                }
                else -> AppText(
                    text = "Touchez un domino de votre main.",
                    style = Typo.small,
                    color = Ink.Muted,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        if (hand.isEmpty()) {
            Box(Modifier.height(82.dp), contentAlignment = Alignment.Center) {
                AppText("Main vide", style = Typo.small, color = Ink.Muted)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // La main se replie sur plusieurs rangées et rétrécit si besoin,
                // pour tenir dans l'écran sans défilement.
                val rows = when {
                    hand.size <= 8 -> 1
                    hand.size <= 16 -> 2
                    else -> 3
                }
                val perRow = (hand.size + rows - 1) / rows
                val gap = 5.dp
                val tileW = ((maxWidth - gap * (perRow - 1)) / perRow).coerceIn(22.dp, 46.dp)
                val tileH = tileW * 1.85f
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (row in hand.chunked(perRow)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (tile in row) {
                                HandTile(
                                    tile = tile,
                                    selected = selectedTile == tile,
                                    playable = turn == 0 && !over && canPlay(tile),
                                    enabled = turn == 0 && !over,
                                    accent = accent,
                                    width = tileW,
                                    height = tileH,
                                    onClick = { onTile(tile) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        HintBar(
            text = "Posez un domino dont un chiffre correspond à une extrémité.",
            accent = accent,
        )
    }
}

@Composable
private fun HandTile(
    tile: Domino,
    selected: Boolean,
    playable: Boolean,
    enabled: Boolean,
    accent: Color,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Ink.Card)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    selected -> accent
                    playable -> accent.copy(alpha = 0.60f)
                    else -> Ink.Line
                },
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 6.dp)) {
            val w = size.width
            val h = size.height
            drawLine(
                color = Ink.Line,
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1.4f,
            )
            val spread = min(w, h / 2f) * 0.30f
            drawPips(tile.a, w / 2f, h / 4f, spread, Ink.Text)
            drawPips(tile.b, w / 2f, h * 0.75f, spread, Ink.Text)
        }
    }
}

/** Une chaîne de dominos horizontale, avec retour à la ligne automatique. */
private fun DrawScope.drawChain(chain: List<Domino>, accent: Color) {
    if (chain.isEmpty()) {
        val w = size.width * 0.56f
        val h = size.height * 0.20f
        val x = (size.width - w) / 2f
        val y = (size.height - h) / 2f
        drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(14f))
        drawRoundRect(Ink.Line, Offset(x, y), Size(w, h), CornerRadius(14f), style = Stroke(2f))
        drawCircle(Ink.Muted.copy(alpha = 0.35f), 3.5f, Offset(size.width / 2f - 9f, size.height / 2f))
        drawCircle(Ink.Muted.copy(alpha = 0.35f), 3.5f, Offset(size.width / 2f + 9f, size.height / 2f))
        return
    }

    val perRow = 5
    val gap = 5f
    val padX = 8f
    val padY = 8f
    val rows = (chain.size + perRow - 1) / perRow
    val availW = size.width - padX * 2f
    val availH = size.height - padY * 2f
    var s = (availW - gap * (perRow - 1)) / (perRow * 2f)
    val needed = rows * s + (rows - 1) * gap
    if (needed > availH) s = (availH - (rows - 1) * gap) / rows
    if (s < 8f) s = 8f

    val blockH = rows * s + (rows - 1) * gap
    var y = (size.height - blockH) / 2f
    for (r in 0 until rows) {
        val count = min(perRow, chain.size - r * perRow)
        val rowW = count * 2f * s + (count - 1) * gap
        var x = (size.width - rowW) / 2f
        for (c in 0 until count) {
            drawDomino(
                tile = chain[r * perRow + c],
                x = x,
                y = y,
                s = s,
                first = r == 0 && c == 0,
                last = r == rows - 1 && c == count - 1,
                accent = accent,
            )
            if (c < count - 1) {
                drawLine(
                    color = Ink.Line,
                    start = Offset(x + 2f * s, y + s / 2f),
                    end = Offset(x + 2f * s + gap, y + s / 2f),
                    strokeWidth = 1.5f,
                )
            }
            x += 2f * s + gap
        }
        y += s + gap
    }
}

private fun DrawScope.drawDomino(
    tile: Domino,
    x: Float,
    y: Float,
    s: Float,
    first: Boolean,
    last: Boolean,
    accent: Color,
) {
    val corner = CornerRadius(s * 0.10f)
    drawRoundRect(Color.White, Offset(x, y), Size(s * 2f, s), corner)
    drawRoundRect(Ink.Line, Offset(x, y), Size(s * 2f, s), corner, style = Stroke(s * 0.025f))
    drawLine(
        color = Ink.Line,
        start = Offset(x + s, y + s * 0.08f),
        end = Offset(x + s, y + s * 0.92f),
        strokeWidth = s * 0.025f,
    )
    val spread = s * 0.30f
    drawPips(tile.a, x + s / 2f, y + s / 2f, spread, Ink.Text)
    drawPips(tile.b, x + s * 1.5f, y + s / 2f, spread, Ink.Text)

    // Les deux extrémités ouvertes de la chaîne.
    if (first) {
        drawRoundRect(
            color = accent,
            topLeft = Offset(x + s * 0.07f, y + s * 0.07f),
            size = Size(s * 0.86f, s * 0.86f),
            cornerRadius = CornerRadius(s * 0.08f),
            style = Stroke(s * 0.07f),
        )
    }
    if (last) {
        drawRoundRect(
            color = accent,
            topLeft = Offset(x + s * 1.07f, y + s * 0.07f),
            size = Size(s * 0.86f, s * 0.86f),
            cornerRadius = CornerRadius(s * 0.08f),
            style = Stroke(s * 0.07f),
        )
    }
}

/** Points d'un dé de 0 à 9, sur une grille de 3 × 3. */
private fun DrawScope.drawPips(value: Int, cx: Float, cy: Float, spread: Float, color: Color) {
    val radius = (spread * 0.17f).coerceAtLeast(1.3f)
    fun pip(col: Int, row: Int) {
        drawCircle(color, radius = radius, center = Offset(cx + (col - 1) * spread, cy + (row - 1) * spread))
    }
    when (value) {
        1 -> pip(1, 1)
        2 -> {
            pip(0, 0)
            pip(2, 2)
        }
        3 -> {
            pip(0, 0)
            pip(1, 1)
            pip(2, 2)
        }
        4 -> {
            pip(0, 0)
            pip(2, 0)
            pip(0, 2)
            pip(2, 2)
        }
        5 -> {
            pip(0, 0)
            pip(2, 0)
            pip(1, 1)
            pip(0, 2)
            pip(2, 2)
        }
        6 -> {
            pip(0, 0)
            pip(0, 1)
            pip(0, 2)
            pip(2, 0)
            pip(2, 1)
            pip(2, 2)
        }
        7 -> {
            pip(0, 0)
            pip(0, 1)
            pip(0, 2)
            pip(2, 0)
            pip(2, 1)
            pip(2, 2)
            pip(1, 1)
        }
        8 -> {
            pip(0, 0)
            pip(1, 0)
            pip(2, 0)
            pip(0, 1)
            pip(2, 1)
            pip(0, 2)
            pip(1, 2)
            pip(2, 2)
        }
        9 -> {
            for (c in 0..2) for (r in 0..2) pip(c, r)
        }
    }
}
