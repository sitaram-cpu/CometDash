package com.sitaram.cometdash

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sitaram.cometdash.databinding.ItemHighScoreBinding

/**
 * Displays the high-score list and updates on its own whenever [submitList]
 * is called with fresh data - this is the "display information from an
 * array/database, changing based on user interaction" challenge: every
 * time the player finishes a game and (optionally) saves a new score, this
 * list is re-read from HighScoreStore and the RecyclerView refreshes.
 */
class HighScoreAdapter(
    private var scores: List<HighScore> = emptyList(),
    private var highlightTimestamp: Long? = null
) : RecyclerView.Adapter<HighScoreAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHighScoreBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitList(newScores: List<HighScore>, newHighlightTimestamp: Long? = highlightTimestamp) {
        scores = newScores
        highlightTimestamp = newHighlightTimestamp
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHighScoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = scores[position]
        val context = holder.binding.root.context
        holder.binding.rankText.text = context.getString(R.string.rank_number_placeholder, position + 1)
        holder.binding.nameText.text = entry.name
        holder.binding.scoreText.text = entry.score.toString()
        holder.binding.newBadge.visibility =
            if (highlightTimestamp != null && entry.timestampMillis == highlightTimestamp) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
    }

    override fun getItemCount(): Int = scores.size
}
