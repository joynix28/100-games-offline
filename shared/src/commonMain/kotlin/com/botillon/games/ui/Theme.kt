package com.botillon.games.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette « papier & encre » : fond chaud, cartes nettes, accents vifs. */
object Ink {
    val Bg = Color(0xFFF6F1E7)
    val BgDeep = Color(0xFFEFE7D8)
    val Card = Color(0xFFFFFFFF)
    val CardAlt = Color(0xFFF3ECDF)
    val Text = Color(0xFF201D1A)
    val Soft = Color(0xFF5C554C)
    val Muted = Color(0xFF8C8376)
    val Line = Color(0xFFE3DACA)

    val Coral = Color(0xFFE4572E)
    val Teal = Color(0xFF17A398)
    val Blue = Color(0xFF3A6EA5)
    val Violet = Color(0xFF7B5EA7)
    val Mustard = Color(0xFFE3B23C)
    val Green = Color(0xFF5B8C5A)
    val Pink = Color(0xFFD96C8A)

    val Accents = listOf(Coral, Teal, Blue, Violet, Mustard, Green, Pink)
}

object Typo {
    var family: FontFamily = FontFamily.Default

    val display: TextStyle
        get() = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp, fontFamily = family)
    val title: TextStyle
        get() = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp, fontFamily = family)
    val heading: TextStyle
        get() = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = family)
    val body: TextStyle
        get() = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = family)
    val small: TextStyle
        get() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = family)
    val label: TextStyle
        get() = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontFamily = family)
    val button: TextStyle
        get() = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp, fontFamily = family)
    val big: TextStyle
        get() = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, fontFamily = family)
}
