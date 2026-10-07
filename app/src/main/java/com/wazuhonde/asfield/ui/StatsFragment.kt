package com.wazuhonde.asfield.ui

import android.os.Bundle
import android.text.TextPaint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.wazuhonde.asfield.R
import com.wazuhonde.asfield.data.AppRepository
import com.wazuhonde.asfield.databinding.FragmentStatsBinding
import com.wazuhonde.asfield.model.Player

class StatsFragment : Fragment(), AppRepository.DataChangeListener {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private var players = emptyList<Player>()
    private var period = Period.SESSION
    private var sortField = SortField.NAME
    private var ascending = true

    private enum class Period { SESSION, TODAY, WEEK, ALL_TIME }

    private enum class SortField(val label: String) {
        NAME("Player name"),
        MATCHES("Matches"),
        WINS("Wins"),
        LOSSES("Losses"),
        DRAWS("Draws")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository.getInstance(requireContext())
        repository.addListener(this)

        binding.spinnerSort.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            SortField.entries.map { it.label }
        )
        binding.spinnerSort.setSelection(0)
        binding.spinnerSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortField = SortField.entries[position]
                renderTable()
            }
        }
        binding.radioStatsPeriod.setOnCheckedChangeListener { _, checkedId ->
            period = when (checkedId) {
                R.id.radio_today -> Period.TODAY
                R.id.radio_week -> Period.WEEK
                R.id.radio_all_time -> Period.ALL_TIME
                else -> Period.SESSION
            }
            renderTable()
        }
        binding.btnSortDirection.setOnClickListener {
            ascending = !ascending
            binding.btnSortDirection.text = if (ascending) "Ascending" else "Descending"
            renderTable()
        }
        refreshUi()
    }

    private fun refreshUi() {
        players = repository.getPlayers()
        renderTable()
    }

    private fun renderTable() {
        if (_binding == null) return
        binding.statsTable.removeAllViews()
        val density = resources.displayMetrics.density
        val headers = listOf("Player", "Matches", "Wins", "Losses", "Draws")
        val ordered = when (sortField) {
            SortField.NAME -> players.sortedBy { it.name.lowercase() }
            SortField.MATCHES -> players.sortedBy { values(it).matches }
            SortField.WINS -> players.sortedBy { values(it).wins }
            SortField.LOSSES -> players.sortedBy { values(it).losses }
            SortField.DRAWS -> players.sortedBy { values(it).draws }
        }.let { if (ascending) it else it.reversed() }
        val rowValues = ordered.map { player ->
            val stats = values(player)
            listOf(player.name, stats.matches.toString(), stats.wins.toString(), stats.losses.toString(), stats.draws.toString())
        }
        val widths = calculateColumnWidths(headers, rowValues, density)
        val header = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        headers.forEachIndexed { index, title ->
            header.addView(cell(title, widths[index], true, density))
        }
        binding.statsTable.addView(header)

        rowValues.forEach { values ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_card))
            }
            values.forEachIndexed { index, value -> row.addView(cell(value, widths[index], false, density)) }
            binding.statsTable.addView(row)
        }
    }

    private fun calculateColumnWidths(headers: List<String>, rows: List<List<String>>, density: Float): List<Int> {
        val paint = TextPaint().apply { textSize = 13f * density }
        val padding = (20 * density).toInt()
        return headers.indices.map { column ->
            val widest = (listOf(headers[column]) + rows.map { it[column] })
                .maxOf { paint.measureText(it).toInt() }
            widest + padding
        }
    }

    private data class StatValues(val matches: Int, val wins: Int, val losses: Int, val draws: Int)

    private fun values(player: Player): StatValues = when (period) {
        Period.SESSION -> StatValues(player.sessionMatchesPlayed, player.sessionWins, player.sessionLosses, player.sessionDraws)
        Period.TODAY -> StatValues(player.dayMatchesPlayed, player.dayWins, player.dayLosses, player.dayDraws)
        Period.WEEK -> StatValues(player.weekMatchesPlayed, player.weekWins, player.weekLosses, player.weekDraws)
        Period.ALL_TIME -> StatValues(player.matchesPlayed, player.wins, player.losses, player.draws)
    }

    private fun cell(text: String, widthPx: Int, header: Boolean, density: Float): TextView =
        TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            this.text = text
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ContextCompat.getColor(requireContext(), if (header) R.color.white else R.color.text_primary))
            textSize = if (header) 12f else 13f
            if (header) setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
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
