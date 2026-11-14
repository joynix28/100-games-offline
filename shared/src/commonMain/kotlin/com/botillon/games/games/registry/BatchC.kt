package com.botillon.games.games.registry

import com.botillon.games.games.BinaryPuzzleGame
import com.botillon.games.games.CubesGame
import com.botillon.games.games.IntruderGame
import com.botillon.games.games.KakuroGame
import com.botillon.games.games.MagicSquareGame
import com.botillon.games.games.MissingNumberGame
import com.botillon.games.games.SequencesGame
import com.botillon.games.games.SkyscrapersGame
import com.botillon.games.games.TablesGame
import com.botillon.games.games.TwentyFourGame

/** Troisième lot : dix jeux de logique et de nombres, jouables hors-ligne. */
val batchC: List<com.botillon.games.ui.GameEntry> = listOf(
    TwentyFourGame.entry,
    TablesGame.entry,
    MissingNumberGame.entry,
    IntruderGame.entry,
    CubesGame.entry,
    MagicSquareGame.entry,
    SequencesGame.entry,
    KakuroGame.entry,
    BinaryPuzzleGame.entry,
    SkyscrapersGame.entry,
)
