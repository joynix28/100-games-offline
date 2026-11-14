package com.botillon.games

import com.botillon.games.ui.Progress
import com.botillon.games.ui.ProgressStore
import com.botillon.games.ui.Sound
import com.botillon.games.ui.SoundPlayer
import java.io.File
import java.util.Properties
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class DesktopProgressStore : ProgressStore {

    private val file = File(System.getProperty("user.home"), ".100games/progress.properties")

    override fun load(): Progress {
        if (!file.exists()) return Progress()
        return runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val best = props.getProperty("best").orEmpty()
                .split(',')
                .mapNotNull { entry ->
                    val parts = entry.split(':')
                    if (parts.size != 2) return@mapNotNull null
                    val id = parts[0].ifBlank { return@mapNotNull null }
                    val score = parts[1].toLongOrNull() ?: return@mapNotNull null
                    id to score
                }
                .toMap()
            Progress(
                best = best,
                played = props.getProperty("played").orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
                sound = props.getProperty("sound")?.toBooleanStrictOrNull() ?: true,
                haptics = props.getProperty("haptics")?.toBooleanStrictOrNull() ?: true,
                lang = runCatching {
                    com.botillon.games.ui.Lang.valueOf(props.getProperty("lang", "FR"))
                }.getOrDefault(com.botillon.games.ui.Lang.FR),
                zoom = props.getProperty("zoom")?.toFloatOrNull() ?: 1f,
            )
        }.getOrDefault(Progress())
    }

    override fun save(progress: Progress) {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            props.setProperty("best", progress.best.entries.joinToString(",") { "${it.key}:${it.value}" })
            props.setProperty("played", progress.played.joinToString(","))
            props.setProperty("sound", progress.sound.toString())
            props.setProperty("haptics", progress.haptics.toString())
            props.setProperty("lang", progress.lang.name)
            props.setProperty("zoom", progress.zoom.toString())
            file.outputStream().use { props.store(it, "100 Games Offline") }
        }
    }
}

class DesktopSoundPlayer : SoundPlayer {
    private val clips = mutableMapOf<Sound, Clip>()

    private fun clipFor(sound: Sound): Clip? = clips.getOrPut(sound) {
        val name = when (sound) {
            Sound.TAP -> "tap"
            Sound.WIN -> "win"
            Sound.LOSE -> "lose"
            Sound.MATCH -> "match"
            Sound.FLIP -> "flip"
            Sound.POINT -> "point"
            Sound.UI -> "ui"
        }
        val stream = javaClass.getResourceAsStream("/sounds/$name.wav") ?: return null
        val audio: AudioInputStream = AudioSystem.getAudioInputStream(stream)
        AudioSystem.getClip().apply {
            open(audio)
            audio.close()
        }
    }.takeIf { it.isOpen }

    override fun play(sound: Sound) {
        val clip = clipFor(sound) ?: return
        runCatching {
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }
    }
}
