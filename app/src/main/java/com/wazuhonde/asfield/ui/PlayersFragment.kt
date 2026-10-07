package com.wazuhonde.asfield.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.data.AppRepository
import com.wazuhonde.asfield.databinding.DialogAddGroupBinding
import com.wazuhonde.asfield.databinding.FragmentPlayersBinding
import com.wazuhonde.asfield.databinding.ItemPlayerSelectBinding
import com.wazuhonde.asfield.model.GroupRule
import com.wazuhonde.asfield.model.Player
import com.wazuhonde.asfield.model.PlayerGroup

class PlayersFragment : Fragment(), AppRepository.DataChangeListener {

    private var _binding: FragmentPlayersBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: AppRepository
    private lateinit var adapter: PlayersAdapter
    private lateinit var groupAdapter: GroupsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository.getInstance(requireContext())
        repository.addListener(this)

        setupRecyclerView()
        setupAddPlayer()
        setupGroups()
        refreshUi()
    }

    private fun setupRecyclerView() {
        adapter = PlayersAdapter(
            onTogglePresence = { player ->
                repository.togglePlayerPresence(player.id)
            },
            onDeletePlayer = { player ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Player")
                    .setMessage("Are you sure you want to delete ${player.name} from the roster?")
                    .setPositiveButton("Delete") { _, _ ->
                        repository.deletePlayer(player.id)
                        Toast.makeText(requireContext(), "${player.name} deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEditPlayer = { player -> showEditPlayerDialog(player) },
            getGroups = { repository.getGroups() }
        )
        binding.rvPlayers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlayers.adapter = adapter
    }

    private fun setupGroups() {
        groupAdapter = GroupsAdapter(
            getPlayerName = { id -> repository.getPlayers().find { it.id == id }?.name ?: "Unknown" },
            onDeleteGroup = { group ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Group")
                    .setMessage("Delete group '${group.name}'?")
                    .setPositiveButton("Delete") { _, _ -> repository.deleteGroup(group.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEditGroup = { group -> showAddGroupDialog(group) }
        )
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroups.adapter = groupAdapter
        binding.btnCreateGroup.setOnClickListener { showAddGroupDialog() }
    }

    private fun showEditPlayerDialog(player: Player) {
        val input = EditText(requireContext()).apply {
            setText(player.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(48, 8, 48, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Player Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Player name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    repository.updatePlayerName(player.id, name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddGroupDialog(existingGroup: PlayerGroup? = null) {
        val players = repository.getPlayers()
        if (players.size < 2) {
            Toast.makeText(requireContext(), "Need at least 2 players to form a group or pair", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAddGroupBinding.inflate(layoutInflater)
        val selectedPlayerIds = existingGroup?.playerIds?.toMutableSet() ?: mutableSetOf()
        dialogBinding.etGroupName.setText(existingGroup?.name.orEmpty())
        when (existingGroup?.rule) {
            GroupRule.SAME_MATCH -> dialogBinding.rbSameMatch.isChecked = true
            GroupRule.OPPOSING_TEAMS -> dialogBinding.rbOpposingTeams.isChecked = true
            GroupRule.SAME_TEAM -> dialogBinding.rbSameTeam.isChecked = true
            null -> Unit
        }
        dialogBinding.rvSelectPlayers.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvSelectPlayers.adapter = PlayerSelectAdapter(players, selectedPlayerIds) { id, checked ->
            if (checked) selectedPlayerIds.add(id) else selectedPlayerIds.remove(id)
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Create") { _, _ ->
                val name = dialogBinding.etGroupName.text?.toString()?.trim().orEmpty()
                if (selectedPlayerIds.size < 2) {
                    Toast.makeText(requireContext(), "Please select at least 2 players", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val groupName = if (name.isEmpty()) "Pair (${selectedPlayerIds.size})" else name
                val rule = when {
                    dialogBinding.rbSameMatch.isChecked -> GroupRule.SAME_MATCH
                    dialogBinding.rbOpposingTeams.isChecked -> GroupRule.OPPOSING_TEAMS
                    else -> GroupRule.SAME_TEAM
                }
                val updatedGroup = PlayerGroup(
                    id = existingGroup?.id ?: java.util.UUID.randomUUID().toString(),
                    name = groupName,
                    playerIds = selectedPlayerIds.toList(),
                    rule = rule
                )
                if (existingGroup == null) repository.addGroup(updatedGroup)
                else repository.updateGroup(updatedGroup)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupAddPlayer() {
        binding.btnAddPlayer.setOnClickListener {
            val name = binding.etPlayerName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                binding.tilPlayerName.error = "Please enter a player name"
                return@setOnClickListener
            }
            binding.tilPlayerName.error = null
            repository.addPlayer(name)
            binding.etPlayerName.setText("")
            Toast.makeText(requireContext(), "Added $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshUi() {
        val players = repository.getPlayers()
        val presentCount = players.count { it.isPresent }
        binding.tvPlayersHeader.text = "Roster (${players.size} players • $presentCount active)"

        if (players.isEmpty()) {
            binding.tvEmptyPlayers.visibility = View.VISIBLE
            binding.rvPlayers.visibility = View.GONE
        } else {
            binding.tvEmptyPlayers.visibility = View.GONE
            binding.rvPlayers.visibility = View.VISIBLE
            adapter.submitList(players)
        }

        val groups = repository.getGroups()
        if (groups.isEmpty()) {
            binding.tvEmptyGroups.visibility = View.VISIBLE
            binding.rvGroups.visibility = View.GONE
        } else {
            binding.tvEmptyGroups.visibility = View.GONE
            binding.rvGroups.visibility = View.VISIBLE
            groupAdapter.submitList(groups)
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
        private val selectedPlayerIds: Set<String>,
        private val onSelectionChanged: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<PlayerSelectAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemPlayerSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val player = players[position]
            holder.binding.cbPlayerSelect.text = player.name
            holder.binding.cbPlayerSelect.setOnCheckedChangeListener(null)
            holder.binding.cbPlayerSelect.isChecked = player.id in selectedPlayerIds
            holder.binding.cbPlayerSelect.setOnCheckedChangeListener { _, checked -> onSelectionChanged(player.id, checked) }
        }

        override fun getItemCount(): Int = players.size

        class ViewHolder(val binding: ItemPlayerSelectBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
