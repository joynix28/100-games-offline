package com.botillon.games.ui

import com.botillon.games.games.BreakoutGame
import com.botillon.games.games.Connect4Game
import com.botillon.games.games.Game2048Entry
import com.botillon.games.games.MemoryGame
import com.botillon.games.games.MorpionGame
import com.botillon.games.games.GuessGame
import com.botillon.games.games.HangmanGame
import com.botillon.games.games.MathGame
import com.botillon.games.games.MinesweeperGame
import com.botillon.games.games.NimGame
import com.botillon.games.games.PongGame
import com.botillon.games.games.RpsGame
import com.botillon.games.games.SimonGame
import com.botillon.games.games.SlidingGame
import com.botillon.games.games.SnakeGame

/**
 * Catalogue des jeux. Chaque jeu expose ses variantes ; le hub les compte
 * automatiquement. Pour ajouter un jeu : créer un fichier dans `games/` avec
 * un `GameEntry`, puis l'ajouter ici.
 */
object Catalog {
    val entries: List<GameEntry> = listOf(
        MorpionGame.entry,
        Game2048Entry.entry,
        MemoryGame.entry,
        SnakeGame.entry,
        PongGame.entry,
        BreakoutGame.entry,
        RpsGame.entry,
        GuessGame.entry,
        NimGame.entry,
        SlidingGame.entry,
        SimonGame.entry,
        MinesweeperGame.entry,
        Connect4Game.entry,
        MathGame.entry,
        HangmanGame.entry,
    )

    fun byId(id: String): GameEntry? = entries.firstOrNull { it.id == id }

    val variantCount: Int get() = entries.sumOf { it.variants.size }
}
