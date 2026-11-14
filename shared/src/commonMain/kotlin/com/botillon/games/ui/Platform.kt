package com.botillon.games.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class Sound { TAP, WIN, LOSE, MATCH, FLIP, POINT, UI }

interface SoundPlayer { fun play(sound: Sound) }

interface Haptics {
    fun tap()
    fun success()
    fun error()
}

object NoopSound : SoundPlayer { override fun play(sound: Sound) = Unit }
object NoopHaptics : Haptics {
    override fun tap() = Unit
    override fun success() = Unit
    override fun error() = Unit
}

val LocalSound = staticCompositionLocalOf<SoundPlayer> { NoopSound }
val LocalHaptics = staticCompositionLocalOf<Haptics> { NoopHaptics }

/** Progression locale : record par jeu, réglages. */
data class Progress(
    val best: Map<String, Long> = emptyMap(),
    val played: Set<String> = emptySet(),
    val sound: Boolean = true,
    val haptics: Boolean = true,
) {
    fun bestOf(id: String): Long = best[id] ?: 0L
}

interface ProgressStore {
    fun load(): Progress
    fun save(progress: Progress)
}

sealed interface Screen {
    data object Hub : Screen
    data object Settings : Screen
    data class Play(val gameId: String) : Screen
}

/** Bouton retour système (Android) ; sans effet sur desktop. */
@androidx.compose.runtime.Composable
expect fun HandleBack(enabled: Boolean, onBack: () -> Unit)
