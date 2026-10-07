package com.sitaram.cometdash

/** One entry in the high score list. */
data class HighScore(
    val name: String,
    val score: Int,
    val timestampMillis: Long = System.currentTimeMillis()
)
