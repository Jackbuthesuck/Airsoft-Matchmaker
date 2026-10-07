package com.wazuhonde.asfield.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.wazuhonde.asfield.data.AppRepository
import com.wazuhonde.asfield.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment(), AppRepository.DataChangeListener {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var adapter: HistoryTimelineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository.getInstance(requireContext())
        repository.addListener(this)

        adapter = HistoryTimelineAdapter(
            getPlayerName = { id -> repository.getPlayers().find { it.id == id }?.name ?: "Unknown" },
            onDeleteMatch = { match ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Match Record")
                    .setMessage("Remove this match from history?")
                    .setPositiveButton("Delete") { _, _ -> repository.deleteMatchFromHistory(match.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvMatchHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMatchHistory.adapter = adapter
        binding.btnResetTodayStats.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset Today's Stats?")
                .setMessage("This clears today's and current-session counts. This week's and all-time stats, players, groups, and history stay safe.")
                .setPositiveButton("Reset") { _, _ ->
                    repository.resetTodayStats()
                    Toast.makeText(requireContext(), "Today's stats reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnResetWeekStats.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset This Week's Stats?")
                .setMessage("This clears this week's, today's, and current-session counts. All-time stats, players, groups, and history stay safe.")
                .setPositiveButton("Reset") { _, _ ->
                    repository.resetWeekStats()
                    Toast.makeText(requireContext(), "This week's stats reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refreshUi()
    }

    private fun refreshUi() {
        val history = repository.getMatchHistory()
        val activity = repository.getActivityHistory()
        val isEmpty = history.isEmpty() && activity.isEmpty()
        binding.tvEmptyHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvMatchHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        adapter.submitLists(history, activity)
    }

    override fun onDataChanged() {
        if (isAdded && _binding != null) refreshUi()
    }

    override fun onDestroyView() {
        repository.removeListener(this)
        _binding = null
        super.onDestroyView()
    }
}
