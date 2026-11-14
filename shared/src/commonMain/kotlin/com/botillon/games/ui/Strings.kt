package com.botillon.games.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class Lang { FR, EN }

class Strings(val lang: Lang) {
    private fun t(fr: String, en: String) = if (lang == Lang.FR) fr else en

    val eyebrow = t("SANS PUB · HORS-LIGNE", "NO ADS · OFFLINE")
    val titleTop = "100"
    val titleBottom = t("Jeux", "Games")
    fun subtitle(games: Int, variants: Int) = if (lang == Lang.FR) {
        "$games jeux · $variants variantes · aucun compte"
    } else {
        "$games games · $variants variants · no account"
    }
    val all = t("Tout", "All")
    val search = t("Chercher un jeu", "Find a game")
    val variants = t("variantes", "variants")
    val played = t("essayés", "played")

    val settings = t("RÉGLAGES", "SETTINGS")
    val settingsSub = t("À ta façon", "Your way")
    val sound = t("Sons", "Sound")
    val soundSub = t("Taps, victoires, défaites", "Taps, wins, losses")
    val haptics = t("Vibrations", "Vibration")
    val hapticsSub = t("Retour haptique", "Haptic feedback")
    val language = t("Langue", "Language")
    val zoom = t("Taille de l'interface", "Interface size")
    val zoomSmall = t("Petit", "Small")
    val zoomNormal = t("Normal", "Normal")
    val zoomLarge = t("Grand", "Large")
    val collection = t("COLLECTION", "COLLECTION")
    val gamesAvailable = t("Jeux disponibles", "Games available")
    val variantsCount = t("Variantes", "Variants")
    val tried = t("Jeux essayés", "Games tried")
    val resetRecords = t("RÉINITIALISER LES RECORDS", "RESET RECORDS")
    val resetTitle = t("Effacer les records ?", "Erase the records?")
    val erase = t("EFFACER", "ERASE")
    val cancel = t("ANNULER", "CANCEL")
    val about = t("Sans publicité. Sans compte. Sans connexion.", "No ads. No account. No connection.")

    val won = t("GAGNÉ", "WON")
    val over = t("TERMINÉ", "FINISHED")
    val newRecord = t("nouveau record", "new record")
    val record = t("record", "best")
    val playAgain = t("REJOUER", "PLAY AGAIN")
    val menu = t("MENU", "MENU")
}

val LocalStrings = staticCompositionLocalOf { Strings(Lang.FR) }
