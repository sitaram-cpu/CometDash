package com.sitaram.cometdash

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sitaram.cometdash.databinding.ActivityHighScoresBinding

/**
 * The game's second screen.
 *
 * Data flows BOTH ways across this Activity boundary:
 *  - MainActivity -> here: the score just earned and (if it made the board)
 *    the timestamp to highlight, passed as Intent extras.
 *  - here -> MainActivity: whether the player cleared the board, passed back
 *    as an activity result, so MainActivity can show a confirmation toast.
 */
class HighScoresActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LATEST_SCORE = "extra_latest_score"
        const val EXTRA_HIGHLIGHT_TIMESTAMP = "extra_highlight_timestamp"
        const val EXTRA_SCORES_CLEARED = "extra_scores_cleared"
    }

    private lateinit var binding: ActivityHighScoresBinding
    private lateinit var highScoreStore: HighScoreStore
    private lateinit var adapter: HighScoreAdapter
    private var scoresClearedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHighScoresBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        highScoreStore = HighScoreStore(this)

        val latestScore = if (intent.hasExtra(EXTRA_LATEST_SCORE)) {
            intent.getIntExtra(EXTRA_LATEST_SCORE, 0)
        } else null
        val highlightTimestamp = if (intent.hasExtra(EXTRA_HIGHLIGHT_TIMESTAMP)) {
            intent.getLongExtra(EXTRA_HIGHLIGHT_TIMESTAMP, -1L)
        } else null

        if (latestScore != null) {
            binding.latestResultText.visibility = View.VISIBLE
            binding.latestResultText.text = getString(R.string.latest_result_format, latestScore)
        }

        adapter = HighScoreAdapter(highlightTimestamp = highlightTimestamp)
        binding.highScoresList.layoutManager = LinearLayoutManager(this)
        binding.highScoresList.adapter = adapter

        binding.clearScoresButton.setOnClickListener { confirmClear() }

        refreshList()
    }

    private fun refreshList() {
        val scores = highScoreStore.getScores()
        adapter.submitList(scores)
        binding.emptyStateText.visibility = if (scores.isEmpty()) View.VISIBLE else View.GONE
        binding.highScoresList.visibility = if (scores.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_scores)
            .setMessage(R.string.clear_confirm_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                highScoreStore.clearAll()
                scoresClearedThisSession = true
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun finish() {
        val resultIntent = Intent().putExtra(EXTRA_SCORES_CLEARED, scoresClearedThisSession)
        setResult(RESULT_OK, resultIntent)
        super.finish()
    }
}
