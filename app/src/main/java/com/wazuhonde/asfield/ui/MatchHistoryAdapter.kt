package com.wazuhonde.asfield.ui

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.R
import com.wazuhonde.asfield.databinding.ItemMatchHistoryBinding
import com.wazuhonde.asfield.model.AirsoftMatch
import com.wazuhonde.asfield.model.MatchResult

class MatchHistoryAdapter(
    private val getPlayerName: (String) -> String,
    private val onDeleteMatch: (AirsoftMatch) -> Unit
) : RecyclerView.Adapter<MatchHistoryAdapter.MatchViewHolder>() {

    private val matches = mutableListOf<AirsoftMatch>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<AirsoftMatch>) {
        matches.clear()
        matches.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(matches[position])
    }

    override fun getItemCount(): Int = matches.size

    inner class MatchViewHolder(private val binding: ItemMatchHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(match: AirsoftMatch) {
            val team1Names = match.team1PlayerIds.joinToString(" & ") { getPlayerName(it) }
            val team2Names = match.team2PlayerIds.joinToString(" & ") { getPlayerName(it) }

            binding.tvTeam1Members.text = team1Names
            binding.tvTeam2Members.text = team2Names

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                match.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            binding.tvMatchTime.text = timeAgo

            when (match.result) {
                MatchResult.TEAM1_WIN -> {
                    binding.tvMatchResultBadge.text = "ALPHA WIN"
                    binding.tvMatchResultBadge.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.team_alpha)
                    )
                }
                MatchResult.TEAM2_WIN -> {
                    binding.tvMatchResultBadge.text = "BRAVO WIN"
                    binding.tvMatchResultBadge.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.team_bravo)
                    )
                }
                MatchResult.DRAW -> {
                    binding.tvMatchResultBadge.text = "DRAW"
                    binding.tvMatchResultBadge.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.primary)
                    )
                }
                MatchResult.NONE -> {
                    binding.tvMatchResultBadge.text = "IN PROGRESS"
                    binding.tvMatchResultBadge.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                    )
                }
            }

            binding.btnDeleteHistory.setOnClickListener {
                onDeleteMatch(match)
            }
        }
    }
}
