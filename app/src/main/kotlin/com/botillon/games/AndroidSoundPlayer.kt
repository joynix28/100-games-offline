package com.botillon.games

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.botillon.games.ui.Sound
import com.botillon.games.ui.SoundPlayer

class AndroidSoundPlayer(context: Context) : SoundPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mapOf(
        Sound.TAP to pool.load(context, R.raw.tap, 1),
        Sound.WIN to pool.load(context, R.raw.win, 1),
        Sound.LOSE to pool.load(context, R.raw.lose, 1),
        Sound.MATCH to pool.load(context, R.raw.match, 1),
        Sound.FLIP to pool.load(context, R.raw.flip, 1),
        Sound.POINT to pool.load(context, R.raw.point, 1),
        Sound.UI to pool.load(context, R.raw.ui, 1),
    )

    override fun play(sound: Sound) {
        val id = ids[sound] ?: return
        val volume = if (sound == Sound.WIN) 0.6f else 0.45f
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}
