package com.wazuhonde.asfield.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.wazuhonde.asfield.R
import com.wazuhonde.asfield.data.AppRepository
import com.wazuhonde.asfield.data.Matchmaker
import com.wazuhonde.asfield.databinding.FragmentMatchBinding
import com.wazuhonde.asfield.model.AirsoftMatch
import com.wazuhonde.asfield.model.MatchResult

class MatchFragment : Fragment(), AppRepository.DataChangeListener {

    private var _binding: FragmentMatchBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: AppRepository
    private val matchmaker = Matchmaker()
    private var selectedTeamSize = 2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository.getInstance(requireContext())
        repository.addListener(this)

        setupButtons()
        setupMatchSizeSelector()
        refreshUi()
    }

    private fun setupButtons() {
        binding.btnGenerateMatch.setOnClickListener {
            generateMatch()
        }

        binding.btnManualMatch.setOnClickListener {
            repository.getCurrentMatch()?.let { currentMatch ->
                showAdjustMatchDialog(currentMatch)
            } ?: showManualMatchDialog()
        }

        binding.btnCancelMatch.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cancel Current Match?")
                .setMessage("This removes the current match without changing player statistics or match history.")
                .setPositiveButton("Cancel Match") { _, _ -> repository.saveCurrentMatch(null) }
                .setNegativeButton("Keep Match", null)
                .show()
        }

        binding.btnWinAlpha.setOnClickListener {
            recordCurrentMatchResult(MatchResult.TEAM1_WIN)
        }

        binding.btnWinBravo.setOnClickListener {
            recordCurrentMatchResult(MatchResult.TEAM2_WIN)
        }

        binding.btnDraw.setOnClickListener {
            recordCurrentMatchResult(MatchResult.DRAW)
        }
    }

    private fun setupMatchSizeSelector() {
        binding.toggleMatchSize.check(R.id.btn_size_2v2)
        binding.toggleMatchSize.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedTeamSize = when (checkedId) {
                R.id.btn_size_1v1 -> 1
                R.id.btn_size_3v3 -> 3
                R.id.btn_size_4v4 -> 4
                else -> 2
            }
            repository.getCurrentMatch()?.let {
                selectedTeamSize = it.team1PlayerIds.size
                binding.toggleMatchSize.check(
                    when (selectedTeamSize) {
                        1 -> R.id.btn_size_1v1
                        3 -> R.id.btn_size_3v3
                        4 -> R.id.btn_size_4v4
                        else -> R.id.btn_size_2v2
                    }
                )
            }
        }
    }

    private fun generateMatch() {
        val players = repository.getPlayers()
        val groups = repository.getGroups()
        val currentMatch = repository.getCurrentMatch()
        val currentMatchIds = currentMatch?.let {
            it.team1PlayerIds.toSet() + it.team2PlayerIds
        } ?: emptySet()

        when (val result = matchmaker.generate2v2Match(
            players,
            groups,
            selectedTeamSize,
            currentMatchIds
        )) {
            is Matchmaker.MatchmakingResult.Success -> {
                repository.saveCurrentMatch(result.match)
                result.message?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                }
            }
            is Matchmaker.MatchmakingResult.Error -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Cannot Generate Match")
                    .setMessage(result.reason)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showManualMatchDialog() {
        repository.saveCurrentMatch(
            AirsoftMatch(
                team1PlayerIds = List(selectedTeamSize) { "" },
                team2PlayerIds = List(selectedTeamSize) { "" }
            )
        )
        Toast.makeText(requireContext(), "Blank match created. Fill the slots manually.", Toast.LENGTH_SHORT).show()
    }

    private fun showAdjustMatchDialog(currentMatch: AirsoftMatch) {
        val players = repository.getPlayers().filter { it.isPresent }
        val teamSize = currentMatch.team1PlayerIds.size
        val requiredPlayers = teamSize * 2
        if (players.size < requiredPlayers) {
            Toast.makeText(requireContext(), "Not enough active players for this match size", Toast.LENGTH_SHORT).show()
            return
        }

        val playersById = players.associateBy { it.id }
        val selectors = mutableListOf<Spinner>()
        val selectorAdapters = mutableListOf<ArrayAdapter<String>>()
        val slotOptions = mutableListOf<List<String>>()
        val slotIds = (currentMatch.team1PlayerIds + currentMatch.team2PlayerIds)
            .map { if (it in playersById) it else "" }
            .toMutableList()
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }

        content.addView(TextView(requireContext()).apply {
            text = "Edit each team slot directly. Clear a slot to choose a replacement. Players already assigned to another slot are hidden from each slot's list."
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.text_secondary))
            setPadding(0, 0, 0, 8)
        })
        var rebuilding = false

        fun addTeamSlots(title: String, playerIds: List<String>) {
            content.addView(TextView(requireContext()).apply {
                text = title
                textSize = 14f
                setTextColor(requireContext().getColor(R.color.text_primary))
                setPadding(0, 12, 0, 4)
            })
            playerIds.forEach {
                val spinner = Spinner(requireContext())
                val spinnerAdapter = ArrayAdapter<String>(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    mutableListOf()
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                spinner.adapter = spinnerAdapter

                val clearButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
                    text = "Clear"
                    setTextSize(11f)
                    minimumWidth = 0
                    setPadding(12, 0, 12, 0)
                }
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                row.addView(spinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(clearButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                content.addView(row)
                selectors.add(spinner)
                selectorAdapters.add(spinnerAdapter)
                slotOptions.add(emptyList())
                val slotIndex = selectors.lastIndex
                clearButton.setOnClickListener {
                    if (!rebuilding) {
                        slotIds[slotIndex] = ""
                        rebuilding = true
                        rebuildSlotOptions(selectors, selectorAdapters, slotOptions, slotIds, playersById)
                        rebuilding = false
                    }
                }
            }
        }

        addTeamSlots("TEAM ALPHA", currentMatch.team1PlayerIds)
        addTeamSlots("TEAM BRAVO", currentMatch.team2PlayerIds)

        rebuildSlotOptions(selectors, selectorAdapters, slotOptions, slotIds, playersById)
        selectors.forEachIndexed { slot, spinner ->
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (rebuilding) return
                    slotIds[slot] = slotOptions[slot].getOrNull(position).orEmpty()
                    rebuilding = true
                    rebuildSlotOptions(selectors, selectorAdapters, slotOptions, slotIds, playersById)
                    rebuilding = false
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Adjust ${teamSize}v${teamSize} Players")
            .setView(content)
            .setPositiveButton("Save") { _, _ ->
                if (slotIds.any { it.isEmpty() }) {
                    Toast.makeText(requireContext(), "Fill every team slot before saving", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedIds = slotIds.toList()
                repository.saveCurrentMatch(
                    AirsoftMatch(
                        team1PlayerIds = selectedIds.take(teamSize),
                        team2PlayerIds = selectedIds.drop(teamSize)
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rebuildSlotOptions(
        selectors: List<Spinner>,
        adapters: List<ArrayAdapter<String>>,
        options: MutableList<List<String>>,
        slotIds: List<String>,
        playersById: Map<String, com.wazuhonde.asfield.model.Player>
    ) {
        selectors.forEachIndexed { index, spinner ->
            val usedElsewhere = slotIds.filterIndexed { otherIndex, id ->
                otherIndex != index && id.isNotEmpty()
            }.toSet()
            val availableIds = listOf("") + playersById.keys.filter { it !in usedElsewhere }
            options[index] = availableIds
            adapters[index].clear()
            adapters[index].addAll(availableIds.map { id ->
                if (id.isEmpty()) "— Clear this slot —" else playersById[id]?.name ?: "Unknown"
            })
            adapters[index].notifyDataSetChanged()
            spinner.setSelection(availableIds.indexOf(slotIds[index]).coerceAtLeast(0), false)
        }
    }

    private fun recordCurrentMatchResult(result: MatchResult) {
        val currentMatch = repository.getCurrentMatch() ?: return
        repository.recordMatchOutcome(currentMatch, result)
        Toast.makeText(requireContext(), "Match recorded!", Toast.LENGTH_SHORT).show()
    }

    private fun refreshUi() {
        val players = repository.getPlayers()
        val presentPlayers = players.filter { it.isPresent }
        binding.tvActiveSummary.text = "Active Players: ${presentPlayers.size} / ${players.size}"

        val currentMatch = repository.getCurrentMatch()
        if (currentMatch != null) {
            binding.layoutCurrentMatch.visibility = View.VISIBLE
            binding.toggleMatchSize.isEnabled = false
            binding.btnCancelMatch.visibility = View.VISIBLE
            binding.btnGenerateMatch.text = "Re-roll / Shuffle Match"
            binding.btnManualMatch.text = "Adjust Selected Players"
            val currentTeamSize = currentMatch.team1PlayerIds.size
            selectedTeamSize = currentTeamSize
            binding.toggleMatchSize.check(
                when (currentTeamSize) {
                    1 -> R.id.btn_size_1v1
                    3 -> R.id.btn_size_3v3
                    4 -> R.id.btn_size_4v4
                    else -> R.id.btn_size_2v2
                }
            )
            binding.tvCurrentMatchTitle.text = "CURRENT ${currentTeamSize}v${currentTeamSize} MATCH"

            val pMap = players.associateBy { it.id }

            hideLegacyPlayerViews()
            renderTeamPlayers(binding.layoutTeam1Players, currentMatch.team1PlayerIds, pMap)
            renderTeamPlayers(binding.layoutTeam2Players, currentMatch.team2PlayerIds, pMap)
            val complete = currentMatch.team1PlayerIds.none { it.isBlank() } &&
                currentMatch.team2PlayerIds.none { it.isBlank() }
            binding.btnWinAlpha.isEnabled = complete
            binding.btnWinBravo.isEnabled = complete
            binding.btnDraw.isEnabled = complete
        } else {
            binding.layoutCurrentMatch.visibility = View.GONE
            binding.toggleMatchSize.isEnabled = true
            binding.btnCancelMatch.visibility = View.GONE
            binding.btnGenerateMatch.text = "Generate ${selectedTeamSize}v${selectedTeamSize} Match"
            binding.btnManualMatch.text = "Choose Players Manually"
            binding.btnWinAlpha.isEnabled = false
            binding.btnWinBravo.isEnabled = false
            binding.btnDraw.isEnabled = false
        }

    }

    private fun hideLegacyPlayerViews() {
        listOf(
            binding.tvTeam1Player1, binding.tvTeam1Player1Sub,
            binding.tvTeam1Player2, binding.tvTeam1Player2Sub,
            binding.tvTeam2Player1, binding.tvTeam2Player1Sub,
            binding.tvTeam2Player2, binding.tvTeam2Player2Sub
        ).forEach { it.visibility = View.GONE }
    }

    private fun renderTeamPlayers(
        container: LinearLayout,
        playerIds: List<String>,
        players: Map<String, com.wazuhonde.asfield.model.Player>
    ) {
        container.removeAllViews()
        val density = resources.displayMetrics.density
        playerIds.forEachIndexed { index, playerId ->
            val player = players[playerId]
            val name = TextView(requireContext()).apply {
                text = if (playerId.isBlank()) "Empty slot" else player?.name ?: "Unknown"
                setTextColor(requireContext().getColor(R.color.text_primary))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val nameParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) nameParams.topMargin = (8 * density).toInt()
            container.addView(name, nameParams)

            val stats = TextView(requireContext()).apply {
                text = if (playerId.isBlank()) "Choose a player" else "${player?.matchesPlayed ?: 0} all-time matches"
                setTextColor(requireContext().getColor(R.color.text_secondary))
                textSize = 12f
            }
            container.addView(stats)
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
}
