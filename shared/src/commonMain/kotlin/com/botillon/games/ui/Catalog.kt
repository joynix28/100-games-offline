package com.botillon.games.ui

import com.botillon.games.games.BreakoutGame
import com.botillon.games.games.BubbleGame
import com.botillon.games.games.CheckersGame
import com.botillon.games.games.KnifeHitGame
import com.botillon.games.games.LightsOutGame
import com.botillon.games.games.ColorMatchGame
import com.botillon.games.games.Match3Game
import com.botillon.games.games.Connect4Game
import com.botillon.games.games.DominoesGame
import com.botillon.games.games.Game2048Entry
import com.botillon.games.games.MemoryGame
import com.botillon.games.games.MorpionGame
import com.botillon.games.games.GuessGame
import com.botillon.games.games.HangmanGame
import com.botillon.games.games.MathGame
import com.botillon.games.games.MinesweeperGame
import com.botillon.games.games.NimGame
import com.botillon.games.games.NumberTilesGame
import com.botillon.games.games.PongGame
import com.botillon.games.games.RpsGame
import com.botillon.games.games.SimonGame
import com.botillon.games.games.SlidingGame
import com.botillon.games.games.SudokuGame
import com.botillon.games.games.SnakeGame
import com.botillon.games.games.WaterSortGame
import com.botillon.games.games.WordSearchGame

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
        CheckersGame.entry,
        Match3Game.entry,
        ColorMatchGame.entry,
        WaterSortGame.entry,
        LightsOutGame.entry,
        SudokuGame.entry,
        NumberTilesGame.entry,
        WordSearchGame.entry,
        KnifeHitGame.entry,
        BubbleGame.entry,
        DominoesGame.entry,
    )

    fun byId(id: String): GameEntry? = entries.firstOrNull { it.id == id }

    val variantCount: Int get() = entries.sumOf { it.variants.size }
}
