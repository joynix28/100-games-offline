package com.botillon.games.games.registry

import com.botillon.games.games.BaccaratGame
import com.botillon.games.games.BatailleGame
import com.botillon.games.games.CrazyEightsGame
import com.botillon.games.games.DuelDeDesGame
import com.botillon.games.games.FanTanGame
import com.botillon.games.games.GoFishGame
import com.botillon.games.games.PileOuFaceGame
import com.botillon.games.games.PlusOuMoinsGame
import com.botillon.games.games.PokerDiceGame
import com.botillon.games.games.SnapGame

/** Série A : dix jeux de cartes et de dés ajoutés au catalogue. */
val batchA: List<com.botillon.games.ui.GameEntry> = listOf(
    PileOuFaceGame.entry,
    DuelDeDesGame.entry,
    PlusOuMoinsGame.entry,
    BatailleGame.entry,
    CrazyEightsGame.entry,
    PokerDiceGame.entry,
    BaccaratGame.entry,
    GoFishGame.entry,
    SnapGame.entry,
    FanTanGame.entry,
)
