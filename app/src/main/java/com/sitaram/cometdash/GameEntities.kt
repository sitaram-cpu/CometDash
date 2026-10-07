package com.sitaram.cometdash

/**
 * Small, plain data holders for everything that moves on screen.
 * Keeping these as simple classes (not database/Room entities) keeps the
 * game loop fast - there can be dozens of these updated 60 times a second.
 */

/** The player-controlled ship. Position is stored as a FRACTION of the view
 *  width (0f = left edge, 1f = right edge) so it survives rotation and
 *  resizing without any conversion math. */
class Ship(var xFraction: Float = 0.5f) {
    var targetXFraction: Float = xFraction
}

/** A falling object - either a comet (danger) or a star (collectible).
 *  [isStar] selects which one it is so both can share one update/draw pass. */
data class FallingEntity(
    var xFraction: Float,
    var y: Float,
    val radiusPx: Float,
    var speedPxPerSec: Float,
    val isStar: Boolean
)

/** A short-lived "pop" visual created when the player taps a star.
 *  Driven entirely by an AnimatorSet in GameView - this class only stores
 *  the numbers the animator is currently producing. */
data class PopEffect(
    val x: Float,
    val y: Float,
    var scale: Float = 1f,
    var alpha: Float = 1f
)

/** Tunable numbers for difficulty / pacing, gathered in one place so the
 *  balance of the game can be tweaked without hunting through GameView. */
object GameConfig {
    const val STARTING_LIVES = 3
    const val SHIP_RADIUS_DP = 22f
    const val COMET_RADIUS_DP = 18f
    const val STAR_RADIUS_DP = 14f

    const val BASE_COMET_SPAWN_SECONDS = 1.35f
    const val MIN_COMET_SPAWN_SECONDS = 0.45f
    const val BASE_STAR_SPAWN_SECONDS = 1.9f
    const val MIN_STAR_SPAWN_SECONDS = 0.9f

    const val BASE_FALL_SPEED_DP_PER_SEC = 160f
    const val MAX_SPEED_MULTIPLIER = 2.4f
    /** Seconds of continuous play for the difficulty to reach max ramp. */
    const val RAMP_SECONDS = 60f

    const val STAR_POINTS = 25
    const val SHIELD_DURATION_MILLIS = 8000L
    /** Brief invincibility after a hit so one comet can't drain all lives at once. */
    const val HIT_INVINCIBILITY_MILLIS = 1200L

    /** Number of quick taps on the score display needed to trigger the shield easter egg. */
    const val EASTER_EGG_TAP_COUNT = 5
    const val EASTER_EGG_WINDOW_MILLIS = 2500L
}
