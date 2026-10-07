package com.sitaram.cometdash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sitaram.cometdash.databinding.ActivityMainBinding
import com.sitaram.cometdash.databinding.DialogEnterNameBinding

/**
 * Hosts the game. Responsibilities on purpose kept OUT of GameView:
 * the app bar / menu, the HUD text, dialogs, high-score persistence,
 * the share intent, and keeping score/lives alive across rotation via
 * [GameViewModel].
 */
class MainActivity : AppCompatActivity(), GameView.GameListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: GameViewModel by viewModels()
    private lateinit var highScoreStore: HighScoreStore

    private var pauseMenuItem: MenuItem? = null

    // Easter egg: tap the score display 5 times within 2.5s to raise a shield.
    private var easterEggTapCount = 0
    private var easterEggWindowStart = 0L

    private val highScoresLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cleared = result.data?.getBooleanExtra(HighScoresActivity.EXTRA_SCORES_CLEARED, false) ?: false
        if (cleared) {
            Toast.makeText(this, "High scores cleared.", Toast.LENGTH_SHORT).show()
        }
        // If the player reached the high-score screen from the game-over dialog,
        // bring the dialog back so they still have a way to play again.
        if (binding.gameView.isGameOverNow()) {
            showResultsDialog(binding.gameView.captureState().score)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        highScoreStore = HighScoreStore(this)
        binding.gameView.listener = this

        val isBrandNewGame = !viewModel.initialized
        if (isBrandNewGame) {
            viewModel.resetForNewGame()
            binding.gameView.startNewGame()
            scheduleInstructionsAutoHide()
        } else {
            val snapshot = GameView.StateSnapshot(
                score = viewModel.score,
                lives = viewModel.lives,
                elapsedMillis = viewModel.elapsedMillis,
                shieldActive = viewModel.shieldActive,
                shieldRemainingMillis = viewModel.shieldRemainingMillis,
                isPaused = viewModel.isPaused,
                isGameOver = viewModel.isGameOver
            )
            binding.gameView.restoreState(snapshot)
            binding.instructionsText.visibility = View.GONE
            updatePausedUi(snapshot.isPaused)
            if (snapshot.isGameOver) {
                // The game-over dialog doesn't survive rotation on its own (Android
                // dismisses plain AlertDialogs when the Activity that showed them is
                // destroyed), so re-show the right one - but never re-save the score,
                // which proceedAfterGameOver already guards against.
                binding.gameOverText.visibility = View.VISIBLE
                binding.gameOverText.scaleX = 1f
                binding.gameOverText.scaleY = 1f
                binding.gameOverText.alpha = 1f
                proceedAfterGameOver(snapshot.score)
            }
        }

        binding.scoreText.setOnClickListener { handleScoreTap() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        pauseMenuItem = menu.findItem(R.id.action_pause)
        updatePauseMenuIcon(binding.gameView.isPausedNow())
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_pause -> {
                togglePause()
                true
            }
            R.id.action_high_scores -> {
                openHighScores(latestScore = null, highlightTimestamp = null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun togglePause() {
        if (binding.gameView.isGameOverNow()) return
        val newPaused = !binding.gameView.isPausedNow()
        binding.gameView.setPaused(newPaused)
        updatePausedUi(newPaused)
    }

    private fun updatePausedUi(paused: Boolean) {
        updatePauseMenuIcon(paused)
        binding.pausedOverlayText.visibility = if (paused) View.VISIBLE else View.GONE
    }

    private fun updatePauseMenuIcon(paused: Boolean) {
        pauseMenuItem?.apply {
            setIcon(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
            title = getString(if (paused) R.string.resume else R.string.pause)
        }
    }

    private fun scheduleInstructionsAutoHide() {
        Handler(Looper.getMainLooper()).postDelayed({
            binding.instructionsText.animate().alpha(0f).setDuration(400)
                .withEndAction { binding.instructionsText.visibility = View.GONE }
                .start()
        }, 4000)
    }

    /** The "cheat": 5 taps on the score label within 2.5 seconds raises a shield. */
    private fun handleScoreTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - easterEggWindowStart > GameConfig.EASTER_EGG_WINDOW_MILLIS) {
            easterEggWindowStart = now
            easterEggTapCount = 0
        }
        easterEggTapCount++
        if (easterEggTapCount >= GameConfig.EASTER_EGG_TAP_COUNT) {
            easterEggTapCount = 0
            binding.gameView.activateShield()
            Toast.makeText(this, "Cheat found! Shield activated.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- GameView.GameListener ----------------

    override fun onScoreChanged(score: Int) {
        binding.scoreText.text = getString(R.string.score_label, score)
    }

    override fun onLivesChanged(lives: Int) {
        binding.livesText.text = getString(R.string.lives_label, lives)
    }

    override fun onShieldChanged(active: Boolean) {
        binding.shieldBadge.visibility = if (active) View.VISIBLE else View.GONE
    }

    override fun onGameOver(finalScore: Int) {
        updatePauseMenuIcon(false)
        binding.pausedOverlayText.visibility = View.GONE
        binding.gameOverText.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(450)
                .setInterpolator(OvershootInterpolator())
                .withEndAction { proceedAfterGameOver(finalScore) }
                .start()
        }
    }

    // ---------------- Game over -> high score -> dialog flow ----------------

    private fun proceedAfterGameOver(score: Int) {
        if (!viewModel.highScoreSavedForRun && score > 0 && highScoreStore.qualifiesAsHighScore(score)) {
            promptForName(score)
        } else {
            showResultsDialog(score)
        }
    }

    private fun promptForName(score: Int) {
        val dialogBinding = DialogEnterNameBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_high_score_prompt)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.nameInput.text?.toString().orEmpty()
                val saved = highScoreStore.addScore(name, score)
                viewModel.highScoreSavedForRun = true
                viewModel.lastSavedScoreTimestamp = saved.timestampMillis
                showResultsDialog(score)
            }
            .show()
    }

    private fun showResultsDialog(score: Int) {
        binding.gameOverText.visibility = View.GONE
        AlertDialog.Builder(this)
            .setTitle(R.string.game_over_title)
            .setMessage(getString(R.string.game_over_message, score))
            .setCancelable(false)
            .setPositiveButton(R.string.play_again) { _, _ -> startNewGame() }
            .setNeutralButton(R.string.share_score) { _, _ ->
                shareScore(score)
                showResultsDialog(score) // re-show so the player still has to pick Play Again / High Scores
            }
            .setNegativeButton(R.string.view_high_scores) { _, _ ->
                openHighScores(latestScore = score, highlightTimestamp = viewModel.lastSavedScoreTimestamp)
            }
            .show()
    }

    private fun startNewGame() {
        viewModel.resetForNewGame()
        binding.instructionsText.visibility = View.GONE
        updatePausedUi(false)
        binding.gameView.startNewGame()
    }

    private fun shareScore(score: Int) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, score))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_score)))
    }

    private fun openHighScores(latestScore: Int?, highlightTimestamp: Long?) {
        val intent = Intent(this, HighScoresActivity::class.java).apply {
            latestScore?.let { putExtra(HighScoresActivity.EXTRA_LATEST_SCORE, it) }
            highlightTimestamp?.let { putExtra(HighScoresActivity.EXTRA_HIGHLIGHT_TIMESTAMP, it) }
        }
        highScoresLauncher.launch(intent)
    }

    // ---------------- Lifecycle: this is what makes rotation safe ----------------

    override fun onResume() {
        super.onResume()
        // Keep the pause icon/overlay in sync - the game always comes back from
        // the background paused (see onPause), even if the icon last reflected
        // "running" before we left.
        updatePausedUi(binding.gameView.isPausedNow())
    }

    override fun onPause() {
        super.onPause()
        val snapshot = binding.gameView.captureState()
        // Always come back paused after leaving the Activity (rotation, home button,
        // another app, a dialog from the system) - the player resumes deliberately.
        viewModel.score = snapshot.score
        viewModel.lives = snapshot.lives
        viewModel.elapsedMillis = snapshot.elapsedMillis
        viewModel.shieldActive = snapshot.shieldActive
        viewModel.shieldRemainingMillis = snapshot.shieldRemainingMillis
        viewModel.isGameOver = snapshot.isGameOver
        viewModel.isPaused = true
        binding.gameView.setPaused(true)
    }
}
