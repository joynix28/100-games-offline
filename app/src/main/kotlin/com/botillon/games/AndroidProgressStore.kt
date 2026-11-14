package com.botillon.games

import android.content.Context
import android.content.SharedPreferences
import com.botillon.games.ui.Progress
import com.botillon.games.ui.ProgressStore

class AndroidProgressStore(context: Context) : ProgressStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("games.progress", Context.MODE_PRIVATE)

    override fun load(): Progress {
        val best = prefs.getString("best", "")
            .orEmpty()
            .split(',')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0].ifBlank { return@mapNotNull null }
                val score = parts[1].toLongOrNull() ?: return@mapNotNull null
                id to score
            }
            .toMap()
        val played = prefs.getString("played", "")
            .orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()
        return Progress(
            best = best,
            played = played,
            sound = prefs.getBoolean("sound", true),
            haptics = prefs.getBoolean("haptics", true),
        )
    }

    override fun save(progress: Progress) {
        prefs.edit()
            .putString("best", progress.best.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString("played", progress.played.joinToString(","))
            .putBoolean("sound", progress.sound)
            .putBoolean("haptics", progress.haptics)
            .apply()
    }
}
