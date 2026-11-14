package com.botillon.games.games.registry

import com.botillon.games.games.AnagrammesGame
import com.botillon.games.games.BoggleGame
import com.botillon.games.games.ConnexionGame
import com.botillon.games.games.CryptogrammeGame
import com.botillon.games.games.EchelleGame
import com.botillon.games.games.FrappeGame
import com.botillon.games.games.LettreManquanteGame
import com.botillon.games.games.MotMystereGame
import com.botillon.games.games.OrdreGame
import com.botillon.games.games.PetitBacGame

/** Quatrième lot de jeux de mots, prêt à être fusionné dans le catalogue. */
val batchD: List<com.botillon.games.ui.GameEntry> = listOf(
    AnagrammesGame.entry,
    MotMystereGame.entry,
    EchelleGame.entry,
    LettreManquanteGame.entry,
    ConnexionGame.entry,
    CryptogrammeGame.entry,
    BoggleGame.entry,
    FrappeGame.entry,
    OrdreGame.entry,
    PetitBacGame.entry,
)
