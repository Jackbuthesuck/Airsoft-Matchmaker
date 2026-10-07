package com.wazuhonde.asfield.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.data.AppRepository
import com.wazuhonde.asfield.databinding.DialogAddGroupBinding
import com.wazuhonde.asfield.databinding.FragmentGroupsBinding
import com.wazuhonde.asfield.databinding.ItemPlayerSelectBinding
import com.wazuhonde.asfield.model.GroupRule
import com.wazuhonde.asfield.model.Player
import com.wazuhonde.asfield.model.PlayerGroup

class GroupsFragment : Fragment(), AppRepository.DataChangeListener {

    private var _binding: FragmentGroupsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: AppRepository
    private lateinit var adapter: GroupsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository.getInstance(requireContext())
        repository.addListener(this)

        setupRecyclerView()
        binding.btnCreateGroup.setOnClickListener {
            showAddGroupDialog()
        }
        refreshUi()
    }

    private fun setupRecyclerView() {
        adapter = GroupsAdapter(
            getPlayerName = { id ->
                repository.getPlayers().find { it.id == id }?.name ?: "Unknown"
            },
            onDeleteGroup = { group ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Group")
                    .setMessage("Delete group '${group.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        repository.deleteGroup(group.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroups.adapter = adapter
    }

    private fun showAddGroupDialog() {
        val players = repository.getPlayers()
        if (players.size < 2) {
            Toast.makeText(requireContext(), "Need at least 2 players in roster to form a group/pair", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAddGroupBinding.inflate(layoutInflater)
        val selectedPlayerIds = mutableSetOf<String>()

        val selectAdapter = PlayerSelectAdapter(players) { playerId, isChecked ->
            if (isChecked) selectedPlayerIds.add(playerId) else selectedPlayerIds.remove(playerId)
        }
        dialogBinding.rvSelectPlayers.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvSelectPlayers.adapter = selectAdapter

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Create") { _, _ ->
                val name = dialogBinding.etGroupName.text?.toString()?.trim() ?: ""
                val groupName = if (name.isEmpty()) "Pair (${selectedPlayerIds.size})" else name

                if (selectedPlayerIds.size < 2) {
                    Toast.makeText(requireContext(), "Please select at least 2 players", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val rule = when {
                    dialogBinding.rbSameTeam.isChecked -> GroupRule.SAME_TEAM
                    dialogBinding.rbSameMatch.isChecked -> GroupRule.SAME_MATCH
                    dialogBinding.rbOpposingTeams.isChecked -> GroupRule.OPPOSING_TEAMS
                    else -> GroupRule.SAME_TEAM
                }

                val group = PlayerGroup(
                    name = groupName,
                    playerIds = selectedPlayerIds.toList(),
                    rule = rule
                )

                repository.addGroup(group)
                Toast.makeText(requireContext(), "Group '$groupName' created!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshUi() {
        val groups = repository.getGroups()
        if (groups.isEmpty()) {
            binding.tvEmptyGroups.visibility = View.VISIBLE
            binding.rvGroups.visibility = View.GONE
        } else {
            binding.tvEmptyGroups.visibility = View.GONE
            binding.rvGroups.visibility = View.VISIBLE
            adapter.submitList(groups)
        }
    }

    override fun onDataChanged() {
        if (isAdded && _binding != null) {
            refreshUi()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repository.removeListener(this)
        _binding = null
    }

    private class PlayerSelectAdapter(
        private val players: List<Player>,
        private val onSelectionChanged: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<PlayerSelectAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPlayerSelectBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val player = players[position]
            holder.binding.cbPlayerSelect.text = player.name
            holder.binding.cbPlayerSelect.setOnCheckedChangeListener(null)
            holder.binding.cbPlayerSelect.isChecked = false
            holder.binding.cbPlayerSelect.setOnCheckedChangeListener { _, isChecked ->
                onSelectionChanged(player.id, isChecked)
            }
        }

        override fun getItemCount(): Int = players.size

        class ViewHolder(val binding: ItemPlayerSelectBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
