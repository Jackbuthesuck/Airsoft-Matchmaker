package com.wazuhonde.asfield.ui

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.R
import com.wazuhonde.asfield.databinding.ItemActivityEventBinding
import com.wazuhonde.asfield.databinding.ItemMatchHistoryBinding
import com.wazuhonde.asfield.model.ActivityEvent
import com.wazuhonde.asfield.model.AirsoftMatch
import com.wazuhonde.asfield.model.MatchResult

class HistoryTimelineAdapter(
    private val getPlayerName: (String) -> String,
    private val onDeleteMatch: (AirsoftMatch) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class TimelineItem {
        abstract val timestamp: Long
        data class Match(val value: AirsoftMatch) : TimelineItem() {
            override val timestamp: Long get() = value.timestamp
        }
        data class Activity(val value: ActivityEvent) : TimelineItem() {
            override val timestamp: Long get() = value.timestamp
        }
    }

    private val items = mutableListOf<TimelineItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitLists(matches: List<AirsoftMatch>, activities: List<ActivityEvent>) {
        items.clear()
        items.addAll(matches.map { TimelineItem.Match(it) })
        items.addAll(activities.map { TimelineItem.Activity(it) })
        items.sortByDescending { it.timestamp }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is TimelineItem.Match -> TYPE_MATCH
        is TimelineItem.Activity -> TYPE_ACTIVITY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MATCH) {
            MatchHolder(ItemMatchHistoryBinding.inflate(inflater, parent, false))
        } else {
            ActivityHolder(ItemActivityEventBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TimelineItem.Match -> (holder as MatchHolder).bind(item.value)
            is TimelineItem.Activity -> (holder as ActivityHolder).bind(item.value)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class MatchHolder(private val binding: ItemMatchHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(match: AirsoftMatch) {
            binding.tvTeam1Members.text = match.team1PlayerIds.joinToString(" & ") { getPlayerName(it) }
            binding.tvTeam2Members.text = match.team2PlayerIds.joinToString(" & ") { getPlayerName(it) }
            binding.tvMatchTime.text = DateUtils.getRelativeTimeSpanString(
                match.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            when (match.result) {
                MatchResult.TEAM1_WIN -> setBadge("ALPHA WIN", R.color.team_alpha)
                MatchResult.TEAM2_WIN -> setBadge("BRAVO WIN", R.color.team_bravo)
                MatchResult.DRAW -> setBadge("DRAW", R.color.primary)
                MatchResult.NONE -> setBadge("IN PROGRESS", R.color.text_secondary)
            }
            binding.btnDeleteHistory.setOnClickListener { onDeleteMatch(match) }
        }

        private fun setBadge(text: String, color: Int) {
            binding.tvMatchResultBadge.text = text
            binding.tvMatchResultBadge.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, color)
            )
        }
    }

    class ActivityHolder(private val binding: ItemActivityEventBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(event: ActivityEvent) {
            binding.tvActivityMessage.text = event.message
            binding.tvActivityTime.text = DateUtils.getRelativeTimeSpanString(
                event.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            val alignment = when (event.kind) {
                "presence_joined" -> Gravity.START
                "presence_left" -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            binding.root.gravity = alignment
            binding.tvActivityMessage.gravity = alignment
            binding.tvActivityTime.gravity = alignment
        }
    }

    companion object {
        private const val TYPE_MATCH = 1
        private const val TYPE_ACTIVITY = 2
    }
}
