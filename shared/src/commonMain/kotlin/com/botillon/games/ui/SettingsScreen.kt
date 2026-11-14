package com.botillon.games.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    progress: Progress,
    onBack: () -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onLang: (Lang) -> Unit,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val s = LocalStrings.current
    var confirm by remember { mutableStateOf(false) }

    Backdrop {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleButton(onClick = onBack) {
                    drawChevronLeft(center, size.minDimension, Ink.Soft)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    AppText(s.settings, style = Typo.label, color = Ink.Coral)
                    AppText(s.settingsSub, style = Typo.title, color = Ink.Text)
                }
            }

            Spacer(Modifier.height(20.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingRow(s.sound, s.soundSub, progress.sound, onToggleSound)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Ink.Line))
                    SettingRow(s.haptics, s.hapticsSub, progress.haptics, onToggleHaptics)
                }
            }

            Spacer(Modifier.height(12.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    AppText(s.language, style = Typo.body.copy(fontSize = 15.sp), color = Ink.Text)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ChoiceChip("Français", progress.lang == Lang.FR) { onLang(Lang.FR) }
                        Spacer(Modifier.width(8.dp))
                        ChoiceChip("English", progress.lang == Lang.EN) { onLang(Lang.EN) }
                    }
                    Spacer(Modifier.height(16.dp))
                    AppText(s.zoom, style = Typo.body.copy(fontSize = 15.sp), color = Ink.Text)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ChoiceChip(s.zoomSmall, progress.zoom < 0.95f) { onZoom(0.88f) }
                        Spacer(Modifier.width(8.dp))
                        ChoiceChip(s.zoomNormal, progress.zoom in 0.95f..1.05f) { onZoom(1f) }
                        Spacer(Modifier.width(8.dp))
                        ChoiceChip(s.zoomLarge, progress.zoom > 1.05f) { onZoom(1.14f) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Panel(Modifier.fillMaxWidth(), color = Ink.CardAlt) {
                Column(Modifier.padding(16.dp)) {
                    AppText(s.collection, style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(8.dp))
                    StatLine(s.gamesAvailable, "${Catalog.entries.size}")
                    StatLine(s.variantsCount, "${Catalog.variantCount}")
                    StatLine(s.tried, "${progress.played.size}")
                }
            }

            Spacer(Modifier.weight(1f))

            SecondaryButton(s.resetRecords, { confirm = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            AppText(
                "100 Games Offline · 1.1",
                style = Typo.label.copy(fontSize = 9.sp),
                color = Ink.Muted,
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Center,
            )
            AppText(
                s.about,
                style = Typo.small,
                color = Ink.Muted,
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Center,
            )
        }

        if (confirm) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.Bg.copy(alpha = 0.86f))
                    .clickable { confirm = false },
                contentAlignment = Alignment.Center,
            ) {
                Panel(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(s.resetTitle, style = Typo.title, color = Ink.Text)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(s.erase, { onReset(); confirm = false }, Modifier.fillMaxWidth(), color = Ink.Coral)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(s.cancel, { confirm = false }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Ink.Teal else Ink.Card)
            .then(if (selected) Modifier else Modifier.border(1.dp, Ink.Line, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        AppText(label, style = Typo.small, color = if (selected) Color.White else Ink.Soft)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = Typo.body.copy(fontSize = 15.sp), color = Ink.Text)
            AppText(subtitle, style = Typo.small, color = Ink.Muted)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Switch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "switch")
    Box(
        Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) Ink.Teal else Ink.Line)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = (3 + knob * 20).dp).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        AppText(label, style = Typo.body, color = Ink.Soft, modifier = Modifier.weight(1f))
        AppText(value, style = Typo.body, color = Ink.Text)
    }
}
