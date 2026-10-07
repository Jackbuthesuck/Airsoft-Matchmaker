package com.wazuhonde.asfield.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.databinding.ItemGroupBinding
import com.wazuhonde.asfield.model.PlayerGroup

class GroupsAdapter(
    private val getPlayerName: (String) -> String,
    private val onDeleteGroup: (PlayerGroup) -> Unit,
    private val onEditGroup: (PlayerGroup) -> Unit = {}
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    private val groups = mutableListOf<PlayerGroup>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<PlayerGroup>) {
        groups.clear()
        groups.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount(): Int = groups.size

    inner class GroupViewHolder(private val binding: ItemGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: PlayerGroup) {
            binding.tvGroupName.text = group.name
            binding.tvRuleBadge.text = group.rule.displayName

            val memberNames = group.playerIds.joinToString(", ") { getPlayerName(it) }
            binding.tvGroupMembers.text = "Members: $memberNames"

            binding.btnDeleteGroup.setOnClickListener {
                onDeleteGroup(group)
            }

            binding.btnEditGroup.setOnClickListener {
                onEditGroup(group)
            }
        }
    }
}
