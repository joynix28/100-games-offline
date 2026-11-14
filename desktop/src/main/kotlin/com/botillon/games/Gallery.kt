package com.botillon.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.botillon.games.ui.App
import com.botillon.games.ui.NoopHaptics
import com.botillon.games.ui.NoopSound
import com.botillon.games.ui.Progress
import com.botillon.games.ui.ProgressStore
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private class GalleryStore(private var progress: Progress) : ProgressStore {
    override fun load(): Progress = progress
    override fun save(progress: Progress) { this.progress = progress }
}

/** ./gradlew :desktop:gallery */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "/tmp/opencode/games").apply { mkdirs() }
    val density = 2f
    val scene = ImageComposeScene(
        width = (440 * density).toInt(),
        height = (900 * density).toInt(),
        density = Density(density),
    ) {
        Box(Modifier.fillMaxSize()) {
            App(
                store = GalleryStore(
                    Progress(
                        best = mapOf("2048" to 4096, "morpion" to 100, "snake" to 180, "memory" to 640),
                        played = setOf("2048", "morpion", "snake"),
                    ),
                ),
                sound = NoopSound,
                haptics = NoopHaptics,
            )
        }
    }

    var clock = 0L
    fun frames(n: Int) {
        repeat(n) {
            clock += 16_000_000
            scene.render(clock).close()
            System.gc()
        }
    }

    fun snap(name: String) {
        val image = scene.render(clock)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        image.close()
        System.gc()
        println("capture  $name.png")
    }

    fun tap(x: Float, y: Float) {
        scene.sendPointerEvent(PointerEventType.Press, Offset(x * density, y * density), timeMillis = clock / 1_000_000)
        frames(2)
        scene.sendPointerEvent(PointerEventType.Release, Offset(x * density, y * density), timeMillis = clock / 1_000_000)
        frames(8)
    }

    frames(20)
    snap("01-hub")

    tap(325f, 228f)
    frames(12)
    snap("02-jeu")

    tap(38f, 34f)
    frames(12)
    tap(402f, 40f)
    frames(10)
    snap("03-reglages")

    println("captures dans ${out.absolutePath}")
    scene.close()
}
