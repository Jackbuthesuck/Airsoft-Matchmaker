package com.wazuhonde.asfield.ui

import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.databinding.ItemStatBinding
import com.wazuhonde.asfield.model.Player

class StatsAdapter : RecyclerView.Adapter<StatsAdapter.StatViewHolder>() {
    enum class Period { SESSION, TODAY, WEEK, ALL_TIME }

    private val players = mutableListOf<Player>()
    private var visiblePeriods = setOf(Period.SESSION)

    fun submitList(newPlayers: List<Player>) {
        players.clear()
        players.addAll(newPlayers)
        notifyDataSetChanged()
    }

    fun setVisiblePeriods(periods: Set<Period>) {
        visiblePeriods = periods
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): StatViewHolder =
        StatViewHolder(ItemStatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: StatViewHolder, position: Int) = holder.bind(players[position])

    override fun getItemCount(): Int = players.size

    inner class StatViewHolder(private val binding: ItemStatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(player: Player) {
            binding.tvStatPlayerName.text = player.name
            val lines = mutableListOf<String>()
            if (Period.SESSION in visiblePeriods) {
                lines += "Current session: ${player.sessionMatchesPlayed} matches | W: ${player.sessionWins}  L: ${player.sessionLosses}  D: ${player.sessionDraws}"
            }
            if (Period.TODAY in visiblePeriods) {
                lines += "Today: ${player.dayMatchesPlayed} matches | W: ${player.dayWins}  L: ${player.dayLosses}  D: ${player.dayDraws}"
            }
            if (Period.WEEK in visiblePeriods) {
                lines += "This week: ${player.weekMatchesPlayed} matches | W: ${player.weekWins}  L: ${player.weekLosses}  D: ${player.weekDraws}"
            }
            if (Period.ALL_TIME in visiblePeriods) {
                lines += "All time: ${player.matchesPlayed} matches | W: ${player.wins}  L: ${player.losses}  D: ${player.draws}"
            }
            binding.tvStatValues.text = lines.joinToString("\n")
            binding.tvStatValues.visibility = if (lines.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
