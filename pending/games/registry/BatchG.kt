package com.botillon.games.games.registry

import com.botillon.games.games.AirHockeyGame
import com.botillon.games.games.ArcheryGame
import com.botillon.games.games.BasketGame
import com.botillon.games.games.BilliardsGame
import com.botillon.games.games.BottleFlipGame
import com.botillon.games.games.BowlingGame
import com.botillon.games.games.DartsGame
import com.botillon.games.games.FruitSliceGame
import com.botillon.games.games.MiniGolfGame
import com.botillon.games.games.PenaltyGame

val batchG: List<com.botillon.games.ui.GameEntry> = listOf(
    FruitSliceGame.entry,
    BottleFlipGame.entry,
    BasketGame.entry,
    PenaltyGame.entry,
    ArcheryGame.entry,
    DartsGame.entry,
    BowlingGame.entry,
    MiniGolfGame.entry,
    AirHockeyGame.entry,
    BilliardsGame.entry,
)
