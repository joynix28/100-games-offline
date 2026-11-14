package com.botillon.games.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import games.shared.generated.resources.Res
import games.shared.generated.resources.space_grotesk_bold
import games.shared.generated.resources.space_grotesk_medium
import games.shared.generated.resources.space_grotesk_regular
import org.jetbrains.compose.resources.Font
import kotlin.math.max

@Composable
fun App(
    store: ProgressStore,
    sound: SoundPlayer,
    haptics: Haptics,
) {
    val family = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
        Font(Res.font.space_grotesk_medium, FontWeight.SemiBold),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
        Font(Res.font.space_grotesk_bold, FontWeight.ExtraBold),
        Font(Res.font.space_grotesk_bold, FontWeight.Black),
    )
    Typo.family = family

    var progress by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Hub) }

    fun update(transform: (Progress) -> Progress) {
        val next = transform(progress)
        progress = next
        store.save(next)
    }

    val effectiveSound = remember(progress.sound, sound) { if (progress.sound) sound else NoopSound }
    val effectiveHaptics = remember(progress.haptics, haptics) { if (progress.haptics) haptics else NoopHaptics }

    CompositionLocalProvider(
        LocalSound provides effectiveSound,
        LocalHaptics provides effectiveHaptics,
    ) {
        HandleBack(enabled = screen != Screen.Hub) { screen = Screen.Hub }

        Crossfade(targetState = screen, animationSpec = tween(200), label = "nav") { current ->
            when (current) {
                Screen.Hub -> HubScreen(
                    entries = Catalog.entries,
                    progress = progress,
                    onPlay = { id -> screen = Screen.Play(id) },
                    onSettings = { screen = Screen.Settings },
                )

                Screen.Settings -> SettingsScreen(
                    progress = progress,
                    onBack = { screen = Screen.Hub },
                    onToggleSound = { on -> update { it.copy(sound = on) } },
                    onToggleHaptics = { on -> update { it.copy(haptics = on) } },
                    onReset = {
                        update { p -> Progress(sound = p.sound, haptics = p.haptics) }
                    },
                )

                is Screen.Play -> {
                    val entry = Catalog.byId(current.gameId)
                    if (entry == null) {
                        screen = Screen.Hub
                    } else {
                        GameHost(
                            entry = entry,
                            best = progress.bestOf(entry.id),
                            onResult = { score ->
                                update { p ->
                                    p.copy(
                                        best = p.best + (entry.id to max(p.bestOf(entry.id), score)),
                                        played = p.played + entry.id,
                                    )
                                }
                            },
                            onExit = { screen = Screen.Hub },
                        )
                    }
                }
            }
        }
    }
}
