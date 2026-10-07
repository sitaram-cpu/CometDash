package com.sitaram.cometdash

import androidx.lifecycle.ViewModel

/**
 * Holds the game's "meaningful" state - score, lives, how long the run has
 * gone on, and shield status - OUTSIDE the View and OUTSIDE the Activity.
 *
 * ViewModel instances survive configuration changes (like a screen
 * rotation) automatically, as long as they're fetched through
 * ViewModelProvider the same way each time. That's what lets this game
 * "handle rotation sensibly": when the device is rotated, MainActivity and
 * GameView are destroyed and recreated, but this object is not, so the
 * player's score and lives are never lost. Only the exact on-screen
 * positions of falling comets/stars reset - that's a cosmetic restart of
 * the current wave, not a loss of progress.
 */
class GameViewModel : ViewModel() {

    var score: Int = 0
    var lives: Int = GameConfig.STARTING_LIVES
    var elapsedMillis: Long = 0L
    var shieldActive: Boolean = false
    var shieldRemainingMillis: Long = 0L
    var isGameOver: Boolean = false
    var isPaused: Boolean = false

    /** True once this ViewModel has been initialized by the Activity at least once. */
    var initialized: Boolean = false

    /** Guards against saving the same run's score twice if the device is rotated
     *  while the "new high score" name-entry dialog is on screen. */
    var highScoreSavedForRun: Boolean = false

    /** Timestamp of the entry just saved to the high-score board, kept here (not
     *  as a plain Activity field) so it survives rotation too. */
    var lastSavedScoreTimestamp: Long? = null

    fun resetForNewGame() {
        score = 0
        lives = GameConfig.STARTING_LIVES
        elapsedMillis = 0L
        shieldActive = false
        shieldRemainingMillis = 0L
        isGameOver = false
        isPaused = false
        highScoreSavedForRun = false
        lastSavedScoreTimestamp = null
        initialized = true
    }
}
