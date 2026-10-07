package com.sitaram.cometdash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/**
 * The game itself: a custom View that overrides both onDraw (to render the
 * ship, comets and stars) and onTouchEvent (to steer the ship and pop
 * stars). Everything is driven by a Choreographer-based loop so motion
 * stays smooth and tied to the display's refresh rate.
 *
 * GameView does not know about Activities, dialogs, or SharedPreferences -
 * it only reports what happened through [GameListener]. That keeps it easy
 * to test and easy to reuse state across rotation (see [captureState] /
 * [restoreState] and GameViewModel).
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    interface GameListener {
        fun onScoreChanged(score: Int)
        fun onLivesChanged(lives: Int)
        fun onShieldChanged(active: Boolean)
        fun onGameOver(finalScore: Int)
    }

    data class StateSnapshot(
        val score: Int,
        val lives: Int,
        val elapsedMillis: Long,
        val shieldActive: Boolean,
        val shieldRemainingMillis: Long,
        val isPaused: Boolean,
        val isGameOver: Boolean
    )

    var listener: GameListener? = null

    private val density = resources.displayMetrics.density
    private val shipRadius = GameConfig.SHIP_RADIUS_DP * density
    private val cometRadius = GameConfig.COMET_RADIUS_DP * density
    private val starRadius = GameConfig.STAR_RADIUS_DP * density

    private val ship = Ship()
    private val comets = mutableListOf<FallingEntity>()
    private val stars = mutableListOf<FallingEntity>()
    private val popEffects = mutableListOf<PopEffect>()
    private val activePopAnimators = mutableListOf<AnimatorSet>()
    private val bgStars = mutableListOf<FloatArray>() // [xFraction, yFraction, phase]

    private var score = 0
    private var lives = GameConfig.STARTING_LIVES
    private var elapsedMillis = 0L
    private var running = false
    private var paused = false
    private var gameOver = false
    private var shieldActive = false
    private var shieldRemainingMillis = 0L
    private var invincibleRemainingMillis = 0L

    private var cometSpawnTimer = GameConfig.BASE_COMET_SPAWN_SECONDS
    private var starSpawnTimer = GameConfig.BASE_STAR_SPAWN_SECONDS
    private var lastFrameNanos = 0L
    private var dragging = false
    private val random = java.util.Random()

    // Paints are created once and reused - never allocate inside onDraw.
    private val bgPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.space_black) }
    private val bgStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.star_white) }
    private val shipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.ship_cyan) }
    private val shipFinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.ship_cyan_dark) }
    private val cockpitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.space_black) }
    private val cometPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.comet_orange) }
    private val cometTailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.comet_orange)
        alpha = 110
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.star_yellow) }
    private val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.shield_green)
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
    }
    private val popRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.star_yellow)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
    }

    private val shipPath = Path()
    private val starPath = Path()

    init {
        contentDescription = resources.getString(R.string.content_desc_game_area)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bgStars.isEmpty() && w > 0 && h > 0) {
            repeat(50) {
                bgStars.add(floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.28f))
            }
        }
    }

    // ---------------- Public control surface (used by MainActivity) ----------------

    fun startNewGame() {
        resetTransientState()
        score = 0
        lives = GameConfig.STARTING_LIVES
        elapsedMillis = 0L
        gameOver = false
        paused = false
        ship.xFraction = 0.5f
        ship.targetXFraction = 0.5f
        reportInitialState()
        startLoop()
    }

    fun restoreState(snapshot: StateSnapshot) {
        resetTransientState()
        score = snapshot.score
        lives = snapshot.lives
        elapsedMillis = snapshot.elapsedMillis
        shieldActive = snapshot.shieldActive
        shieldRemainingMillis = snapshot.shieldRemainingMillis
        gameOver = snapshot.isGameOver
        paused = snapshot.isPaused
        reportInitialState()
        startLoop()
    }

    private fun resetTransientState() {
        comets.clear(); stars.clear(); popEffects.clear()
        cancelPopAnimators()
        invincibleRemainingMillis = 0L
        cometSpawnTimer = GameConfig.BASE_COMET_SPAWN_SECONDS
        starSpawnTimer = GameConfig.BASE_STAR_SPAWN_SECONDS
        shieldActive = false
        shieldRemainingMillis = 0L
    }

    private fun reportInitialState() {
        listener?.onScoreChanged(score)
        listener?.onLivesChanged(lives)
        listener?.onShieldChanged(shieldActive)
    }

    fun captureState(): StateSnapshot = StateSnapshot(
        score, lives, elapsedMillis, shieldActive, shieldRemainingMillis, paused, gameOver
    )

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun isPausedNow(): Boolean = paused
    fun isGameOverNow(): Boolean = gameOver

    /** Called from MainActivity when the player finds the tap-the-score easter egg. */
    fun activateShield() {
        if (gameOver) return
        shieldActive = true
        shieldRemainingMillis = GameConfig.SHIELD_DURATION_MILLIS
        listener?.onShieldChanged(true)
        invalidate()
    }

    fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
        cancelPopAnimators()
    }

    // ---------------- Game loop ----------------

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
        var dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        if (dt > 0.05f) dt = 0.05f // clamp big gaps (e.g. app was backgrounded)

        if (!paused && !gameOver) {
            update(dt)
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun update(dt: Float) {
        elapsedMillis += (dt * 1000).toLong()
        val elapsedSeconds = elapsedMillis / 1000f
        val speedMultiplier = (1f + elapsedSeconds / GameConfig.RAMP_SECONDS)
            .coerceAtMost(GameConfig.MAX_SPEED_MULTIPLIER)

        ship.xFraction += (ship.targetXFraction - ship.xFraction) * (dt * 10f).coerceAtMost(1f)

        cometSpawnTimer -= dt
        if (cometSpawnTimer <= 0f) {
            spawnComet(speedMultiplier)
            cometSpawnTimer = (GameConfig.BASE_COMET_SPAWN_SECONDS / speedMultiplier)
                .coerceAtLeast(GameConfig.MIN_COMET_SPAWN_SECONDS)
        }
        starSpawnTimer -= dt
        if (starSpawnTimer <= 0f) {
            spawnStar(speedMultiplier)
            starSpawnTimer = (GameConfig.BASE_STAR_SPAWN_SECONDS / speedMultiplier)
                .coerceAtLeast(GameConfig.MIN_STAR_SPAWN_SECONDS)
        }

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (invincibleRemainingMillis > 0) invincibleRemainingMillis -= (dt * 1000).toLong()
        if (shieldActive) {
            shieldRemainingMillis -= (dt * 1000).toLong()
            if (shieldRemainingMillis <= 0) {
                shieldActive = false
                listener?.onShieldChanged(false)
            }
        }

        val shipPxX = ship.xFraction * w
        val shipPxY = h - shipRadius * 2.2f

        val cometIterator = comets.iterator()
        while (cometIterator.hasNext()) {
            val c = cometIterator.next()
            c.y += c.speedPxPerSec * dt
            if (c.y - c.radiusPx > h) {
                cometIterator.remove()
                continue
            }
            val dx = c.xFraction * w - shipPxX
            val dy = c.y - shipPxY
            val dist = kotlin.math.hypot(dx, dy)
            if (dist < (c.radiusPx + shipRadius) * 0.8f) {
                cometIterator.remove()
                if (!shieldActive && invincibleRemainingMillis <= 0) {
                    lives -= 1
                    invincibleRemainingMillis = GameConfig.HIT_INVINCIBILITY_MILLIS
                    listener?.onLivesChanged(lives)
                    if (lives <= 0) {
                        gameOver = true
                        listener?.onGameOver(score)
                    }
                }
            }
        }

        val starIterator = stars.iterator()
        while (starIterator.hasNext()) {
            val s = starIterator.next()
            s.y += s.speedPxPerSec * dt
            if (s.y - s.radiusPx > h) {
                starIterator.remove()
            }
        }
    }

    private fun spawnComet(speedMultiplier: Float) {
        comets.add(
            FallingEntity(
                xFraction = 0.08f + random.nextFloat() * 0.84f,
                y = -cometRadius,
                radiusPx = cometRadius,
                speedPxPerSec = GameConfig.BASE_FALL_SPEED_DP_PER_SEC * density * speedMultiplier,
                isStar = false
            )
        )
    }

    private fun spawnStar(speedMultiplier: Float) {
        stars.add(
            FallingEntity(
                xFraction = 0.08f + random.nextFloat() * 0.84f,
                y = -starRadius,
                radiusPx = starRadius,
                speedPxPerSec = GameConfig.BASE_FALL_SPEED_DP_PER_SEC * density * speedMultiplier * 0.85f,
                isStar = true
            )
        )
    }

    // ---------------- Touch handling ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver || paused) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tappedStar = findStarNear(event.x, event.y)
                if (tappedStar != null) {
                    popStar(tappedStar)
                } else {
                    dragging = true
                    ship.targetXFraction = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    ship.targetXFraction = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findStarNear(x: Float, y: Float): FallingEntity? {
        val w = width.toFloat()
        for (s in stars) {
            val dx = s.xFraction * w - x
            val dy = s.y - y
            if (kotlin.math.hypot(dx, dy) < s.radiusPx * 1.8f) return s
        }
        return null
    }

    /**
     * Pops a star the player tapped. Two ValueAnimators (ring scale, fade
     * alpha) are combined in one AnimatorSet so they play together and
     * finish together - this is the "second animation that interacts with
     * the first via an AnimatorSet" piece, and because it only ever runs
     * in response to a tap, it also covers "animation reacts to touch".
     */
    private fun popStar(star: FallingEntity) {
        stars.remove(star)
        score += GameConfig.STAR_POINTS
        listener?.onScoreChanged(score)

        val w = width.toFloat()
        val effect = PopEffect(x = star.xFraction * w, y = star.y)
        popEffects.add(effect)

        val scaleAnimator = ValueAnimator.ofFloat(1f, 2.4f).apply {
            addUpdateListener { effect.scale = it.animatedValue as Float; invalidate() }
        }
        val alphaAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            addUpdateListener { effect.alpha = it.animatedValue as Float; invalidate() }
        }
        val set = AnimatorSet()
        set.playTogether(scaleAnimator, alphaAnimator)
        set.duration = 380L
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                popEffects.remove(effect)
                activePopAnimators.remove(set)
            }
        })
        activePopAnimators.add(set)
        set.start()
    }

    private fun cancelPopAnimators() {
        activePopAnimators.forEach { it.removeAllListeners(); it.cancel() }
        activePopAnimators.clear()
    }

    // ---------------- Drawing ----------------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val t = elapsedMillis / 1000f
        for (bs in bgStars) {
            val alpha = (150 + 90 * kotlin.math.sin(t * 2f + bs[2])).toInt().coerceIn(40, 255)
            bgStarPaint.alpha = alpha
            canvas.drawCircle(bs[0] * w, bs[1] * h, 2.5f * density, bgStarPaint)
        }

        for (s in stars) {
            drawStarShape(canvas, s.xFraction * w, s.y, s.radiusPx, starPaint)
        }

        for (c in comets) {
            val cx = c.xFraction * w
            canvas.drawCircle(cx, c.y - c.radiusPx * 1.4f, c.radiusPx * 0.6f, cometTailPaint)
            canvas.drawCircle(cx, c.y, c.radiusPx, cometPaint)
        }

        for (p in popEffects) {
            popRingPaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, starRadius * p.scale, popRingPaint)
        }

        val shouldDrawShip = invincibleRemainingMillis <= 0 || (elapsedMillis / 100) % 2 == 0L
        val shipPxY = h - shipRadius * 2.2f
        if (shouldDrawShip) {
            drawShip(canvas, ship.xFraction * w, shipPxY)
        }
        if (shieldActive) {
            canvas.drawCircle(ship.xFraction * w, shipPxY, shipRadius * 1.6f, shieldPaint)
        }
    }

    private fun drawShip(canvas: Canvas, cx: Float, cy: Float) {
        val r = shipRadius
        shipPath.reset()
        shipPath.moveTo(cx, cy - r * 1.3f)
        shipPath.lineTo(cx + r * 0.85f, cy + r * 0.9f)
        shipPath.lineTo(cx, cy + r * 0.5f)
        shipPath.lineTo(cx - r * 0.85f, cy + r * 0.9f)
        shipPath.close()
        canvas.drawPath(shipPath, shipPaint)

        canvas.drawCircle(cx - r * 0.85f, cy + r * 0.9f, r * 0.18f, shipFinPaint)
        canvas.drawCircle(cx + r * 0.85f, cy + r * 0.9f, r * 0.18f, shipFinPaint)
        canvas.drawCircle(cx, cy - r * 0.1f, r * 0.32f, cockpitPaint)
    }

    private fun drawStarShape(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        starPath.reset()
        val outerR = r
        val innerR = r * 0.45f
        for (i in 0 until 10) {
            val angle = (Math.PI / 5 * i - Math.PI / 2).toFloat()
            val radius = if (i % 2 == 0) outerR else innerR
            val x = cx + radius * kotlin.math.cos(angle)
            val y = cy + radius * kotlin.math.sin(angle)
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }
}
