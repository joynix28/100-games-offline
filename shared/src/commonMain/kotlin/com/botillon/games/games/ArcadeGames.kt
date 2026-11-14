package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/** Pong vertical : on déplace la raquette du bas au doigt. */
object PongGame {
    val entry = GameEntry(
        id = "pong",
        title = "Pong",
        tagline = "Ne laissez pas passer la balle",
        family = Family.ARCADE,
        accent = Ink.Blue,
        variants = listOf("Cool", "Normal", "Féroce"),
    ) { variant, _, finish, _ ->
        Pong(variant, finish)
    }
}

@Composable
private fun Pong(variant: Int, finish: (Long, Boolean) -> Unit) {
    val ballSpeed = listOf(0.52f, 0.70f, 0.92f)[variant]
    val paddleWidth = listOf(0.32f, 0.26f, 0.21f)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var ballX by remember { mutableStateOf(0.5f) }
    var ballY by remember { mutableStateOf(0.5f) }
    var vx by remember { mutableStateOf(0.32f) }
    var vy by remember { mutableStateOf(ballSpeed) }
    var paddle by remember { mutableStateOf(0.5f) }
    var aiPaddle by remember { mutableStateOf(0.5f) }
    var playerScore by remember { mutableIntStateOf(0) }
    var aiScore by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }

    fun resetBall(direction: Float) {
        ballX = 0.5f
        ballY = 0.5f
        vx = if (Random.nextBoolean()) 0.3f else -0.3f
        vy = ballSpeed * direction
    }

    fun point(player: Boolean) {
        if (player) playerScore++ else aiScore++
        sound.play(if (player) Sound.POINT else Sound.LOSE)
        haptics.tap()
        if (playerScore >= 5 || aiScore >= 5) {
            over = true
            finish(playerScore * 100L, playerScore >= 5)
            sound.play(if (playerScore >= 5) Sound.WIN else Sound.LOSE)
        } else {
            resetBall(if (player) -1f else 1f)
        }
    }

    LaunchedEffect(over) {
        if (over) return@LaunchedEffect
        var last = 0L
        while (!over) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val dt = min(0.033f, (now - last) / 1_000_000_000f)
                    last = now
                    ballX += vx * dt
                    ballY += vy * dt
                    if (ballX < 0.02f) {
                        ballX = 0.02f
                        vx = abs(vx)
                    }
                    if (ballX > 0.98f) {
                        ballX = 0.98f
                        vx = -abs(vx)
                    }
                    // Raquette du joueur en bas.
                    if (ballY > 0.90f && vy > 0f) {
                        if (abs(ballX - paddle) < paddleWidth / 2f + 0.02f) {
                            vy = -abs(vy) * 1.02f
                            vx += (ballX - paddle) * 0.9f
                            sound.play(Sound.TAP)
                        } else if (ballY > 1f) {
                            point(false)
                        }
                    }
                    // Raquette IA en haut.
                    if (ballY < 0.10f && vy < 0f) {
                        if (abs(ballX - aiPaddle) < paddleWidth / 2f + 0.02f) {
                            vy = abs(vy) * 1.02f
                            vx += (ballX - aiPaddle) * 0.8f
                        } else if (ballY < 0f) {
                            point(true)
                        }
                    }
                    // L'IA suit la balle avec un peu de retard.
                    val target = ballX + Random.nextFloat() * 0.04f - 0.02f
                    aiPaddle += ((target - aiPaddle) * 2.2f * dt).coerceIn(-dt * 1.4f, dt * 1.4f)
                    aiPaddle = aiPaddle.coerceIn(0.08f, 0.92f)
                    vx = vx.coerceIn(-1.1f, 1.1f)
                    vy = vy.coerceIn(-1.25f, 1.25f)
                } else {
                    last = now
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Vous $playerScore", style = Typo.heading, color = Ink.Coral)
            AppText("IA $aiScore", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .pointerInput(over) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        paddle = (change.position.x / size.width).coerceIn(0.12f, 0.88f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                // Ligne médiane.
                var y = 0f
                while (y < size.height) {
                    drawRect(
                        color = Ink.Line,
                        topLeft = Offset(size.width / 2f - 1.5f, y),
                        size = Size(3f, 12f),
                    )
                    y += 26f
                }
                val paddleW = size.width * paddleWidth
                // Raquette joueur.
                drawRoundRect(
                    color = Ink.Coral,
                    topLeft = Offset(size.width * paddle - paddleW / 2f, size.height * 0.92f),
                    size = Size(paddleW, size.height * 0.028f),
                    cornerRadius = CornerRadius(10f),
                )
                // Raquette IA.
                drawRoundRect(
                    color = Ink.Blue,
                    topLeft = Offset(size.width * aiPaddle - paddleW / 2f, size.height * 0.055f),
                    size = Size(paddleW, size.height * 0.028f),
                    cornerRadius = CornerRadius(10f),
                )
                // Balle.
                drawCircle(
                    color = Ink.Text,
                    radius = size.width * 0.022f,
                    center = Offset(size.width * ballX, size.height * ballY),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HintBar("Faites glisser votre doigt pour déplacer la raquette. Premier à 5 points.")
        Spacer(Modifier.height(8.dp))
        AppText(
            text = if (over) "Partie terminée" else "Balle à ${(vy * 100).toInt().coerceAtLeast(0)} %",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}

/** Casse-briques : trois dispositions de briques, trois vies. */
object BreakoutGame {
    val entry = GameEntry(
        id = "cassebriques",
        title = "Casse-briques",
        tagline = "Vider l'écran, brique par brique",
        family = Family.ARCADE,
        accent = Ink.Pink,
        variants = listOf("Classique", "Dense", "Rapide"),
    ) { variant, _, finish, _ ->
        Breakout(variant, finish)
    }
}

@Composable
private fun Breakout(variant: Int, finish: (Long, Boolean) -> Unit) {
    val rows = listOf(3, 4, 5)[variant]
    val cols = 8
    val speed = listOf(0.55f, 0.70f, 0.88f)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var bricks by remember { mutableStateOf(List(rows * cols) { true }) }
    var ballX by remember { mutableStateOf(0.5f) }
    var ballY by remember { mutableStateOf(0.72f) }
    var vx by remember { mutableStateOf(0.28f) }
    var vy by remember { mutableStateOf(-speed) }
    var paddle by remember { mutableStateOf(0.5f) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var over by remember { mutableStateOf(false) }

    fun resetBall() {
        ballX = 0.5f
        ballY = 0.74f
        vx = if (Random.nextBoolean()) 0.26f else -0.26f
        vy = -speed
    }

    fun loseLife() {
        lives -= 1
        sound.play(Sound.LOSE)
        haptics.error()
        if (lives <= 0) {
            over = true
            finish(score, false)
        } else {
            resetBall()
        }
    }

    LaunchedEffect(over) {
        if (over) return@LaunchedEffect
        var last = 0L
        while (!over) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = min(0.033f, (now - last) / 1_000_000_000f)
                last = now
                ballX += vx * dt
                ballY += vy * dt
                if (ballX < 0.02f) {
                    ballX = 0.02f
                    vx = abs(vx)
                }
                if (ballX > 0.98f) {
                    ballX = 0.98f
                    vx = -abs(vx)
                }
                if (ballY < 0.02f) {
                    ballY = 0.02f
                    vy = abs(vy)
                }
                // Raquette.
                if (ballY > 0.90f && vy > 0f) {
                    if (abs(ballX - paddle) < 0.16f) {
                        vy = -abs(vy)
                        vx += (ballX - paddle) * 1.4f
                        sound.play(Sound.TAP)
                    } else if (ballY > 1.02f) {
                        loseLife()
                    }
                }
                // Briques.
                if (ballY < 0.52f) {
                    val col = (ballX * cols).toInt().coerceIn(0, cols - 1)
                    val row = ((ballY - 0.06f) / 0.09f).toInt().coerceIn(0, rows - 1)
                    val index = row * cols + col
                    if (index in bricks.indices && bricks[index]) {
                        bricks = bricks.toMutableList().also { it[index] = false }
                        score += 10
                        vy = -vy
                        sound.play(Sound.MATCH)
                        haptics.tap()
                        if (bricks.none { it }) {
                            over = true
                            finish(score + lives * 50, true)
                            sound.play(Sound.WIN)
                        }
                    }
                }
                vx = vx.coerceIn(-0.95f, 0.95f)
                vy = vy.coerceIn(-1.05f, 1.05f)
                paddle = paddle.coerceIn(0.16f, 0.84f)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Score $score", style = Typo.heading, color = Ink.Text)
            AppText("Vies $lives", style = Typo.small, color = if (lives == 1) Ink.Coral else Ink.Muted)
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .pointerInput(over) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        paddle = (change.position.x / size.width).coerceIn(0.16f, 0.84f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                val brickW = size.width / cols
                val brickH = size.height * 0.075f
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val index = row * cols + col
                        if (!bricks[index]) continue
                        val color = Ink.Accents[(row + col) % Ink.Accents.size]
                        drawRoundRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(
                                col * brickW + brickW * 0.08f,
                                size.height * 0.06f + row * brickH * 1.22f,
                            ),
                            size = Size(brickW * 0.84f, brickH),
                            cornerRadius = CornerRadius(brickH * 0.28f),
                        )
                    }
                }
                drawRoundRect(
                    color = Ink.Text,
                    topLeft = Offset(size.width * paddle - size.width * 0.16f, size.height * 0.94f),
                    size = Size(size.width * 0.32f, size.height * 0.026f),
                    cornerRadius = CornerRadius(12f),
                )
                drawCircle(
                    color = Ink.Coral,
                    radius = size.width * 0.022f,
                    center = Offset(size.width * ballX, size.height * ballY),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HintBar("Glissez pour déplacer la raquette. Trois vies, ${rows * cols} briques.")
        Spacer(Modifier.height(8.dp))
        AppText(
            text = "${bricks.count { it }} briques restantes",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}
