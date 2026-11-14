package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Bataille navale : couler la flotte de l'ordinateur avant qu'il ne coule la vôtre.
 *
 * Le joueur tire sur la grille ennemie ; l'IA riposte au hasard, puis chasse
 * les cases voisines dès qu'elle a touché un navire. Chaque tir non utilisé
 * rapporte un point bonus en cas de victoire.
 */
object BattleshipGame {
    val entry = GameEntry(
        id = "bataille",
        title = "Bataille navale",
        tagline = "Couler la flotte adverse",
        family = Family.BOARD,
        accent = Ink.Blue,
        variants = listOf("5 × 5", "8 × 8", "10 × 10"),
    ) { variant, _, finish, _ ->
        Battleship(variant, finish)
    }
}

/** Flotte proportionnelle à la grille : 2 à 4 navires, tailles 2 et 3. */
private fun battleFleetSizes(size: Int): List<Int> = when (size) {
    5 -> listOf(3, 2, 2)
    8 -> listOf(3, 2, 2, 3)
    else -> listOf(3, 3, 2, 2)
}

/** Tous les placements possibles d'un navire, horizontaux et verticaux. */
private fun battlePlacements(size: Int, ship: Int): List<Set<Int>> {
    val placements = mutableListOf<Set<Int>>()
    for (row in 0 until size) for (col in 0 until size) {
        if (col + ship <= size) {
            placements += (0 until ship).map { row * size + col + it }.toSet()
        }
        if (row + ship <= size) {
            placements += (0 until ship).map { (row + it) * size + col }.toSet()
        }
    }
    return placements
}

/** Place une flotte entière sans chevauchement, avec plusieurs tentatives. */
private fun battleFleet(size: Int, ships: List<Int>, random: Random): List<Set<Int>> {
    repeat(200) {
        val occupied = mutableSetOf<Int>()
        val fleet = mutableListOf<Set<Int>>()
        var complete = true
        for (ship in ships) {
            val candidates = battlePlacements(size, ship).filter { cells ->
                cells.none { it in occupied }
            }
            if (candidates.isEmpty()) {
                complete = false
                break
            }
            val chosen = candidates[random.nextInt(candidates.size)]
            occupied += chosen
            fleet += chosen
        }
        if (complete) return fleet
    }
    // Repli déterministe : une ligne différente par navire, jamais de collision.
    return ships.mapIndexed { row, ship -> (0 until ship).map { row * size + it }.toSet() }
}

/** Cases orthogonales autour d'un tir, pour la chasse de l'IA. */
private fun battleNeighbors(cell: Int, size: Int): List<Int> {
    val row = cell / size
    val col = cell % size
    val result = mutableListOf<Int>()
    if (row > 0) result += cell - size
    if (row < size - 1) result += cell + size
    if (col > 0) result += cell - 1
    if (col < size - 1) result += cell + 1
    return result
}

@Composable
private fun Battleship(variant: Int, finish: (Long, Boolean) -> Unit) {
    val size = listOf(5, 8, 10)[variant]
    val ships = battleFleetSizes(size)
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var enemyShips by remember { mutableStateOf(battleFleet(size, ships, Random.Default)) }
    var playerShips by remember { mutableStateOf(battleFleet(size, ships, Random.Default)) }
    var playerShots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var aiShots by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var aiQueue by remember { mutableStateOf<List<Int>>(emptyList()) }
    var turn by remember { mutableIntStateOf(0) } // 0 vous, 1 IA, 2 terminé
    var lastPlayerShot by remember { mutableIntStateOf(-1) }
    var lastAiShot by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("À vous de tirer.") }
    var reported by remember { mutableStateOf(false) }

    fun report(score: Long, won: Boolean) {
        if (reported) return
        reported = true
        if (won) {
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
        }
        finish(score, won)
    }

    fun fire(cell: Int) {
        if (turn != 0 || cell in playerShots) return
        val shots = playerShots + cell
        playerShots = shots
        lastPlayerShot = cell
        haptics.tap()

        val hit = enemyShips.any { cell in it }
        sound.play(if (hit) Sound.MATCH else Sound.TAP)
        message = if (hit) "Touché !" else "Dans l'eau."

        if (enemyShips.all { ship -> ship.all { it in shots } }) {
            turn = 2
            message = "Flotte ennemie coulée !"
            report(100L + (size * size - shots.size), true)
        } else {
            turn = 1
        }
    }

    // Riposte de l'IA : voisines d'un touché si possible, hasard sinon.
    LaunchedEffect(turn) {
        if (turn != 1) return@LaunchedEffect
        delay(430)

        val available = (0 until size * size).filter { it !in aiShots }
        if (available.isEmpty()) {
            turn = 0
            return@LaunchedEffect
        }

        val choice = aiQueue.firstOrNull { it in available } ?: available.random()
        val shots = aiShots + choice
        aiShots = shots
        lastAiShot = choice
        aiQueue = aiQueue.filter { it != choice }

        val hit = playerShips.any { choice in it }
        if (hit) {
            sound.play(Sound.MATCH)
            haptics.error()
            val sunkNow = playerShips.any { ship ->
                choice in ship && ship.all { it in shots }
            }
            if (sunkNow) {
                aiQueue = emptyList()
                message = "L'IA coule un navire…"
            } else {
                aiQueue = (aiQueue + battleNeighbors(choice, size).filter { it !in shots }).distinct()
                message = "L'IA vous touche…"
            }

            if (playerShips.all { ship -> ship.all { it in shots } }) {
                turn = 2
                message = "Votre flotte est coulée."
                report(0L, false)
            } else {
                turn = 0
            }
        } else {
            sound.play(Sound.TAP)
            message = "L'IA tire dans l'eau. À vous !"
            turn = 0
        }
    }

    val enemyRemaining = enemyShips.count { ship -> !ship.all { it in playerShots } }
    val playerRemaining = playerShips.count { ship -> !ship.all { it in aiShots } }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText(
                        text = message,
                        style = Typo.heading,
                        color = if (turn == 1) Ink.Blue else Ink.Text,
                        maxLines = 2,
                        softWrap = true,
                    )
                    AppText(
                        text = "Tirs : ${playerShots.size}/${size * size}",
                        style = Typo.small,
                        color = Ink.Muted,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Pill(
                        text = "ENNEMI $enemyRemaining/${enemyShips.size}",
                        color = Ink.Blue,
                        background = Ink.Blue.copy(alpha = 0.10f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Pill(
                        text = "VOUS $playerRemaining/${playerShips.size}",
                        color = Ink.Green,
                        background = Ink.Green.copy(alpha = 0.10f),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        BattleHeader(
            title = "GRILLE ENNEMIE",
            detail = if (turn == 0) "Touchez pour tirer" else "L'IA riposte…",
            color = Ink.Blue,
        )
        Spacer(Modifier.height(4.dp))

        Canvas(
            Modifier
                .size(350.dp)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(size, turn, playerShots) {
                    detectTapGestures { offset ->
                        if (turn != 0) return@detectTapGestures
                        val cellSize = this.size.width / size.toFloat()
                        val col = (offset.x / cellSize).toInt().coerceIn(0, size - 1)
                        val row = (offset.y / cellSize).toInt().coerceIn(0, size - 1)
                        fire(row * size + col)
                    }
                },
        ) {
            drawBattleBoard(
                grid = size,
                ships = enemyShips,
                shots = playerShots,
                lastShot = lastPlayerShot,
                ownFleet = false,
            )
        }

        Spacer(Modifier.height(6.dp))

        BattleHeader(
            title = "VOTRE FLOTTE",
            detail = "${aiShots.size} tir(s) adverse(s)",
            color = Ink.Green,
        )
        Spacer(Modifier.height(4.dp))

        Canvas(
            Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            drawBattleBoard(
                grid = size,
                ships = playerShips,
                shots = aiShots,
                lastShot = lastAiShot,
                ownFleet = true,
            )
        }

        Spacer(Modifier.weight(1f))

        HintBar(
            text = "Touchez la grille ennemie. L'IA chasse après un touché. " +
                "Chaque tir non utilisé rapporte un point bonus.",
            accent = Ink.Blue,
        )
    }
}

@Composable
private fun BattleHeader(title: String, detail: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(title, style = Typo.label, color = color)
        AppText(detail, style = Typo.small, color = Ink.Muted)
    }
}

private fun DrawScope.drawBattleBoard(
    grid: Int,
    ships: List<Set<Int>>,
    shots: Set<Int>,
    lastShot: Int,
    ownFleet: Boolean,
) {
    val cell = this.size.width / grid
    val corner = CornerRadius(cell * 0.16f)

    drawRoundRect(
        color = Ink.Blue.copy(alpha = 0.06f),
        cornerRadius = CornerRadius(16f),
    )

    // La flotte du joueur est visible ; les navires ennemis ne se révèlent que coulés.
    for (ship in ships) {
        val sunk = ship.all { it in shots }
        if (!ownFleet && !sunk) continue
        for (index in ship) {
            val row = index / grid
            val col = index % grid
            val color = when {
                sunk -> Ink.Coral.copy(alpha = if (ownFleet) 0.24f else 0.13f)
                ownFleet -> Ink.Green.copy(alpha = 0.30f)
                else -> Ink.Coral.copy(alpha = 0.13f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(col * cell + 1.5f, row * cell + 1.5f),
                size = Size(cell - 3f, cell - 3f),
                cornerRadius = corner,
            )
        }
    }

    val lineWidth = 1.dp.toPx()
    for (i in 0..grid) {
        drawLine(
            color = Ink.Line,
            start = Offset(i * cell, 0f),
            end = Offset(i * cell, this.size.height),
            strokeWidth = lineWidth,
        )
        drawLine(
            color = Ink.Line,
            start = Offset(0f, i * cell),
            end = Offset(this.size.width, i * cell),
            strokeWidth = lineWidth,
        )
    }

    for (index in shots) {
        val row = index / grid
        val col = index % grid
        val cx = (col + 0.5f) * cell
        val cy = (row + 0.5f) * cell
        val hit = ships.any { index in it }
        if (hit) {
            val radius = cell * 0.24f
            val stroke = (cell * 0.10f).coerceAtLeast(1.5.dp.toPx())
            drawLine(
                color = Ink.Coral,
                start = Offset(cx - radius, cy - radius),
                end = Offset(cx + radius, cy + radius),
                strokeWidth = stroke,
            )
            drawLine(
                color = Ink.Coral,
                start = Offset(cx + radius, cy - radius),
                end = Offset(cx - radius, cy + radius),
                strokeWidth = stroke,
            )
        } else {
            drawCircle(
                color = Ink.Blue.copy(alpha = 0.55f),
                radius = (cell * 0.11f).coerceAtLeast(1.5f),
                center = Offset(cx, cy),
            )
        }
    }

    if (lastShot >= 0) {
        val row = lastShot / grid
        val col = lastShot % grid
        drawRoundRect(
            color = Ink.Mustard,
            topLeft = Offset(col * cell + 1.5f, row * cell + 1.5f),
            size = Size(cell - 3f, cell - 3f),
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
