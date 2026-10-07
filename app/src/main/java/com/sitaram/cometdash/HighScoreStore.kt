package com.sitaram.cometdash

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Simple persistent high-score list. This is the game's "database" -
 * implemented with SharedPreferences + a small hand-rolled JSON array
 * instead of a full SQL database, which keeps the project dependency-free
 * while still giving the HighScoresActivity something real to read, sort,
 * and update based on user interaction.
 */
class HighScoreStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "comet_dash_prefs"
        private const val KEY_SCORES = "high_scores"
        const val MAX_ENTRIES = 10
    }

    fun getScores(): List<HighScore> {
        val raw = prefs.getString(KEY_SCORES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HighScore(
                    name = obj.optString("name", "???"),
                    score = obj.optInt("score", 0),
                    timestampMillis = obj.optLong("timestamp", 0L)
                )
            }.sortedByDescending { it.score }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Whether [score] is good enough to earn a spot on the board (used to decide
     *  whether to prompt the player for their initials after a game). */
    fun qualifiesAsHighScore(score: Int): Boolean {
        if (score <= 0) return false
        val scores = getScores()
        if (scores.size < MAX_ENTRIES) return true
        val lowestOnBoard = scores.minByOrNull { it.score }?.score ?: 0
        return score > lowestOnBoard
    }

    /** Adds [score] under [name], trims the board back down to [MAX_ENTRIES],
     *  and returns the exact entry that was just created (so the caller can
     *  highlight it later without guessing which row it ended up in). */
    fun addScore(name: String, score: Int): HighScore {
        val safeName = name.trim().ifBlank { "YOU" }.take(10)
        val entry = HighScore(safeName, score)
        val updated = (getScores() + entry)
            .sortedByDescending { it.score }
            .take(MAX_ENTRIES)
        save(updated)
        return entry
    }

    fun clearAll() {
        prefs.edit().remove(KEY_SCORES).apply()
    }

    private fun save(scores: List<HighScore>) {
        val array = JSONArray()
        scores.forEach { hs ->
            val obj = JSONObject()
            obj.put("name", hs.name)
            obj.put("score", hs.score)
            obj.put("timestamp", hs.timestampMillis)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SCORES, array.toString()).apply()
    }
}
