package com.wazuhonde.asfield.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.databinding.ItemPlayerBinding
import com.wazuhonde.asfield.databinding.ItemPlayerGroupHeaderBinding
import com.wazuhonde.asfield.model.Player
import com.wazuhonde.asfield.model.PlayerGroup

class PlayersAdapter(
    private val onTogglePresence: (Player) -> Unit,
    private val onDeletePlayer: (Player) -> Unit,
    private val onEditPlayer: (Player) -> Unit,
    private val getGroups: () -> List<PlayerGroup>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data class GroupHeader(val title: String) : Row
        data class PlayerRow(val player: Player) : Row
    }

    private val rows = mutableListOf<Row>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<Player>) {
        rows.clear()
        val playersById = newList.associateBy { it.id }
        val groupedIds = mutableSetOf<String>()

        getGroups()
            .sortedWith(compareBy({ it.rule.displayName }, { it.name }))
            .forEach { group ->
                val members = group.playerIds
                    .mapNotNull { playersById[it] }
                    .sortedBy { it.name.lowercase() }
                if (members.isNotEmpty()) {
                    rows += Row.GroupHeader("[${group.rule.displayName}] ${group.name}")
                    members.forEach {
                        rows += Row.PlayerRow(it)
                        groupedIds += it.id
                    }
                }
            }

        val ungrouped = newList
            .filterNot { it.id in groupedIds }
            .sortedBy { it.name.lowercase() }
        if (ungrouped.isNotEmpty()) {
            rows += Row.GroupHeader("Ungrouped players")
            ungrouped.forEach { rows += Row.PlayerRow(it) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.GroupHeader -> VIEW_TYPE_GROUP_HEADER
        is Row.PlayerRow -> VIEW_TYPE_PLAYER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GROUP_HEADER) {
            GroupHeaderViewHolder(
                ItemPlayerGroupHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            PlayerViewHolder(
                ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.GroupHeader -> (holder as GroupHeaderViewHolder).bind(row.title)
            is Row.PlayerRow -> (holder as PlayerViewHolder).bind(row.player)
        }
    }

    override fun getItemCount(): Int = rows.size

    private class GroupHeaderViewHolder(
        private val binding: ItemPlayerGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvGroupHeader.text = title
        }
    }

    inner class PlayerViewHolder(private val binding: ItemPlayerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(player: Player) {
            binding.tvPlayerName.text = player.name
            binding.tvPlayerStats.visibility = View.GONE
            binding.switchPresent.setOnCheckedChangeListener(null)
            binding.switchPresent.isChecked = player.isPresent

            binding.switchPresent.setOnCheckedChangeListener { _, _ ->
                onTogglePresence(player)
            }

            binding.btnDeletePlayer.setOnClickListener {
                onDeletePlayer(player)
            }

            binding.btnEditPlayer.setOnClickListener {
                onEditPlayer(player)
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_GROUP_HEADER = 0
        const val VIEW_TYPE_PLAYER = 1
    }
}
