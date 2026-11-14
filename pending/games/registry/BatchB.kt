package com.botillon.games.games.registry

import com.botillon.games.games.BallonsGame
import com.botillon.games.games.CibleGame
import com.botillon.games.games.FlappyGame
import com.botillon.games.games.FruitsGame
import com.botillon.games.games.MoucheGame
import com.botillon.games.games.ReflexeGame
import com.botillon.games.games.SauteurGame
import com.botillon.games.games.TaupeGame
import com.botillon.games.games.TourGame
import com.botillon.games.games.ViseeGame

/** Deuxième lot de jeux d'arcade, prêt à être branché au catalogue. */
val batchB: List<com.botillon.games.ui.GameEntry> = listOf(
    TaupeGame.entry,
    ReflexeGame.entry,
    CibleGame.entry,
    FruitsGame.entry,
    FlappyGame.entry,
    SauteurGame.entry,
    TourGame.entry,
    BallonsGame.entry,
    MoucheGame.entry,
    ViseeGame.entry,
)
