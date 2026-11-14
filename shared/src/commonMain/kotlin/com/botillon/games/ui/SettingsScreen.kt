package com.botillon.games.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    progress: Progress,
    onBack: () -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
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
                    AppText("RÉGLAGES", style = Typo.label, color = Ink.Coral)
                    AppText("À ta façon", style = Typo.title, color = Ink.Text)
                }
            }

            Spacer(Modifier.height(20.dp))

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SettingRow(
                        title = "Sons",
                        subtitle = "Taps, victoires, défaites",
                        checked = progress.sound,
                        onChange = onToggleSound,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(Ink.Line),
                    )
                    SettingRow(
                        title = "Vibrations",
                        subtitle = "Retour haptique",
                        checked = progress.haptics,
                        onChange = onToggleHaptics,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Panel(Modifier.fillMaxWidth(), color = Ink.CardAlt) {
                Column(Modifier.padding(18.dp)) {
                    AppText("COLLECTION", style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(10.dp))
                    StatLine("Jeux disponibles", "${Catalog.entries.size}")
                    StatLine("Variantes", "${Catalog.variantCount}")
                    StatLine("Jeux essayés", "${progress.played.size}")
                }
            }

            Spacer(Modifier.weight(1f))

            SecondaryButton("RÉINITIALISER LES RECORDS", { confirm = true }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            AppText(
                "100 Games Offline · 1.0",
                style = Typo.label.copy(fontSize = 9.sp),
                color = Ink.Muted,
                modifier = Modifier.fillMaxWidth(),
                align = androidx.compose.ui.text.style.TextAlign.Center,
            )
            AppText(
                "Sans publicité. Sans compte. Sans connexion.",
                style = Typo.small,
                color = Ink.Muted,
                modifier = Modifier.fillMaxWidth(),
                align = androidx.compose.ui.text.style.TextAlign.Center,
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
                        AppText("Effacer les records ?", style = Typo.title, color = Ink.Text)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(
                            "EFFACER",
                            {
                                onReset()
                                confirm = false
                            },
                            Modifier.fillMaxWidth(),
                            color = Ink.Coral,
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("ANNULER", { confirm = false }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        Box(
            Modifier
                .offset(x = (3 + knob * 20).dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        AppText(label, style = Typo.body, color = Ink.Soft, modifier = Modifier.weight(1f))
        AppText(value, style = Typo.body, color = Ink.Text)
    }
}
