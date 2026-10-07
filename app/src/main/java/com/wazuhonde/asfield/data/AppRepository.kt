package com.wazuhonde.asfield.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wazuhonde.asfield.model.AirsoftMatch
import com.wazuhonde.asfield.model.MatchResult
import com.wazuhonde.asfield.model.Player
import com.wazuhonde.asfield.model.PlayerGroup
import java.time.LocalDate
import java.time.temporal.WeekFields

class AppRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val listeners = mutableListOf<DataChangeListener>()

    interface DataChangeListener {
        fun onDataChanged()
    }

    fun addListener(listener: DataChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: DataChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyDataChanged() {
        listeners.toList().forEach { it.onDataChanged() }
    }

    // Players
    fun getPlayers(): List<Player> {
        val json = prefs.getString(KEY_PLAYERS, null) ?: return normalizePeriodStats(getSamplePlayers())
        val type = object : TypeToken<List<Player>>() {}.type
        val players = try {
            gson.fromJson(json, type) ?: getSamplePlayers()
        } catch (e: Exception) {
            getSamplePlayers()
        }
        val normalized = normalizePeriodStats(players)
        if (normalized != players) {
            prefs.edit().putString(KEY_PLAYERS, gson.toJson(normalized)).apply()
        }
        return normalized
    }

    fun savePlayers(players: List<Player>) {
        prefs.edit().putString(KEY_PLAYERS, gson.toJson(players)).apply()
        notifyDataChanged()
    }

    fun addPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = getPlayers().toMutableList()
        current.add(Player(name = trimmed))
        savePlayers(current)
    }

    fun deletePlayer(playerId: String) {
        val current = getPlayers().filterNot { it.id == playerId }
        savePlayers(current)

        // Also remove player from any group
        val groups = getGroups().toMutableList()
        var groupChanged = false
        for (i in groups.indices) {
            if (groups[i].playerIds.contains(playerId)) {
                val updatedMembers = groups[i].playerIds.filterNot { it == playerId }
                groups[i] = groups[i].copy(playerIds = updatedMembers)
                groupChanged = true
            }
        }
        if (groupChanged) {
            saveGroups(groups.filter { it.playerIds.size >= 2 })
        }
    }

    fun updatePlayerName(playerId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        savePlayers(getPlayers().map { if (it.id == playerId) it.copy(name = trimmed) else it })
    }

    fun togglePlayerPresence(playerId: String) {
        val currentPlayers = getPlayers()
        val returningSessionBaseline = currentPlayers
            .filter { it.id != playerId && it.isPresent }
            .minOfOrNull { it.sessionMatchesPlayed } ?: 0
        val players = currentPlayers.map {
            if (it.id == playerId) {
                val becomingActive = !it.isPresent
                if (becomingActive) {
                    it.copy(
                        isPresent = true,
                        sessionMatchesPlayed = returningSessionBaseline,
                        sessionWins = 0,
                        sessionLosses = 0,
                        sessionDraws = 0
                    )
                } else {
                    it.copy(isPresent = false)
                }
            } else it
        }
        savePlayers(players)
    }

    fun resetTodayStats() {
        val players = getPlayers().map {
            it.copy(
                statsDayKey = currentDayKey(),
                dayMatchesPlayed = 0,
                dayWins = 0,
                dayLosses = 0,
                dayDraws = 0
            )
        }
        savePlayers(players)
    }

    fun resetWeekStats() {
        val players = getPlayers().map {
            it.copy(
                statsWeekKey = currentWeekKey(),
                weekMatchesPlayed = 0,
                weekWins = 0,
                weekLosses = 0,
                weekDraws = 0
            )
        }
        savePlayers(players)
    }

    // Groups
    fun getGroups(): List<PlayerGroup> {
        val json = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        val type = object : TypeToken<List<PlayerGroup>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveGroups(groups: List<PlayerGroup>) {
        prefs.edit().putString(KEY_GROUPS, gson.toJson(groups)).apply()
        notifyDataChanged()
    }

    fun addGroup(group: PlayerGroup) {
        val current = getGroups().toMutableList()
        current.add(group)
        saveGroups(current)
    }

    fun deleteGroup(groupId: String) {
        val current = getGroups().filterNot { it.id == groupId }
        saveGroups(current)
    }

    fun updateGroup(group: PlayerGroup) {
        val current = getGroups().map { if (it.id == group.id) group else it }
        saveGroups(current)
    }

    // Current Match & History
    fun getCurrentMatch(): AirsoftMatch? {
        val json = prefs.getString(KEY_CURRENT_MATCH, null) ?: return null
        return try {
            gson.fromJson(json, AirsoftMatch::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCurrentMatch(match: AirsoftMatch?) {
        if (match == null) {
            prefs.edit().remove(KEY_CURRENT_MATCH).apply()
        } else {
            prefs.edit().putString(KEY_CURRENT_MATCH, gson.toJson(match)).apply()
        }
        notifyDataChanged()
    }

    fun getMatchHistory(): List<AirsoftMatch> {
        val json = prefs.getString(KEY_MATCH_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<AirsoftMatch>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMatchHistory(history: List<AirsoftMatch>) {
        prefs.edit().putString(KEY_MATCH_HISTORY, gson.toJson(history)).apply()
        notifyDataChanged()
    }

    fun recordMatchOutcome(match: AirsoftMatch, result: MatchResult) {
        val completedMatch = match.copy(result = result)

        val players = getPlayers().toMutableList()

        for (i in players.indices) {
            val p = players[i]
            val isTeam1 = completedMatch.team1PlayerIds.contains(p.id)
            val isTeam2 = completedMatch.team2PlayerIds.contains(p.id)

            if (isTeam1 || isTeam2) {
                var newWins = p.wins
                var newLosses = p.losses
                var newDraws = p.draws

                when (result) {
                    MatchResult.TEAM1_WIN -> {
                        if (isTeam1) newWins++ else newLosses++
                    }
                    MatchResult.TEAM2_WIN -> {
                        if (isTeam2) newWins++ else newLosses++
                    }
                    MatchResult.DRAW -> {
                        newDraws++
                    }
                    MatchResult.NONE -> {}
                }

                players[i] = p.copy(
                    matchesPlayed = p.matchesPlayed + 1,
                    wins = newWins,
                    losses = newLosses,
                    draws = newDraws,
                    dayMatchesPlayed = p.dayMatchesPlayed + 1,
                    dayWins = p.dayWins + (newWins - p.wins),
                    dayLosses = p.dayLosses + (newLosses - p.losses),
                    dayDraws = p.dayDraws + (newDraws - p.draws),
                    weekMatchesPlayed = p.weekMatchesPlayed + 1,
                    weekWins = p.weekWins + (newWins - p.wins),
                    weekLosses = p.weekLosses + (newLosses - p.losses),
                    weekDraws = p.weekDraws + (newDraws - p.draws),
                    sessionMatchesPlayed = p.sessionMatchesPlayed + 1,
                    sessionWins = p.sessionWins + (newWins - p.wins),
                    sessionLosses = p.sessionLosses + (newLosses - p.losses),
                    sessionDraws = p.sessionDraws + (newDraws - p.draws),
                    statsDayKey = currentDayKey(),
                    statsWeekKey = currentWeekKey()
                )
            }
        }

        savePlayers(players)

        val history = getMatchHistory().toMutableList()
        history.add(0, completedMatch)
        saveMatchHistory(history)

        saveCurrentMatch(null)
    }

    fun deleteMatchFromHistory(matchId: String) {
        val current = getMatchHistory().filterNot { it.id == matchId }
        saveMatchHistory(current)
    }

    private fun getSamplePlayers(): List<Player> {
        val samples = listOf(
            Player(name = "Alpha One"),
            Player(name = "Ghost"),
            Player(name = "Viper"),
            Player(name = "Shadow"),
            Player(name = "Maverick"),
            Player(name = "Reaper")
        )
        savePlayers(samples)
        return samples
    }

    private fun currentDayKey(): String = LocalDate.now().toString()

    private fun currentWeekKey(): String {
        val today = LocalDate.now()
        val fields = WeekFields.ISO
        return "${today.get(fields.weekBasedYear())}-${today.get(fields.weekOfWeekBasedYear())}"
    }

    private fun normalizePeriodStats(players: List<Player>): List<Player> {
        val dayKey = currentDayKey()
        val weekKey = currentWeekKey()
        return players.map { player ->
            player.copy(
                statsDayKey = dayKey,
                dayMatchesPlayed = if (player.statsDayKey == dayKey) player.dayMatchesPlayed else 0,
                dayWins = if (player.statsDayKey == dayKey) player.dayWins else 0,
                dayLosses = if (player.statsDayKey == dayKey) player.dayLosses else 0,
                dayDraws = if (player.statsDayKey == dayKey) player.dayDraws else 0,
                statsWeekKey = weekKey,
                weekMatchesPlayed = if (player.statsWeekKey == weekKey) player.weekMatchesPlayed else 0,
                weekWins = if (player.statsWeekKey == weekKey) player.weekWins else 0,
                weekLosses = if (player.statsWeekKey == weekKey) player.weekLosses else 0,
                weekDraws = if (player.statsWeekKey == weekKey) player.weekDraws else 0
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "asfield_prefs"
        private const val KEY_PLAYERS = "key_players"
        private const val KEY_GROUPS = "key_groups"
        private const val KEY_CURRENT_MATCH = "key_current_match"
        private const val KEY_MATCH_HISTORY = "key_match_history"

        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AppRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
