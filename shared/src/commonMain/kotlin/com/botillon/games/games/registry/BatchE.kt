package com.botillon.games.games.registry

/**
 * Lot E : dix jeux de plateau et de réflexion supplémentaires.
 * Chaque entrée est autonome et peut être ajoutée au catalogue sans autre dépendance.
 */
val batchE: List<com.botillon.games.ui.GameEntry> = listOf(
    com.botillon.games.games.SerpentsGame.entry,
    com.botillon.games.games.PetitsChevauxGame.entry,
    com.botillon.games.games.ReversiGame.entry,
    com.botillon.games.games.MancalaGame.entry,
    com.botillon.games.games.MoulinGame.entry,
    com.botillon.games.games.PointsCarresGame.entry,
    com.botillon.games.games.BackgammonGame.entry,
    com.botillon.games.games.PegSolitaireGame.entry,
    com.botillon.games.games.HanoiGame.entry,
    com.botillon.games.games.DamesChinoisesGame.entry,
)
