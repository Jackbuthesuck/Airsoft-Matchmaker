package com.wazuhonde.asfield.data

import com.wazuhonde.asfield.model.AirsoftMatch
import com.wazuhonde.asfield.model.GroupRule
import com.wazuhonde.asfield.model.Player
import com.wazuhonde.asfield.model.PlayerGroup
import kotlin.random.Random

class Matchmaker {

    sealed class MatchmakingResult {
        data class Success(val match: AirsoftMatch, val message: String? = null) : MatchmakingResult()
        data class Error(val reason: String) : MatchmakingResult()
    }

    fun generate2v2Match(
        allPlayers: List<Player>,
        groups: List<PlayerGroup>,
        teamSize: Int = 2,
        excludedPlayerIds: Set<String> = emptySet(),
        recentHistory: List<AirsoftMatch> = emptyList()
    ): MatchmakingResult {
        val safeTeamSize = teamSize.coerceIn(1, 4)
        val requiredPlayers = safeTeamSize * 2
        val activePlayers = allPlayers.filter { it.isPresent }
        val replacements = activePlayers.filterNot { it.id in excludedPlayerIds }
        val presentPlayers = if (replacements.size >= requiredPlayers) replacements else activePlayers
        if (presentPlayers.size < requiredPlayers) {
            return MatchmakingResult.Error("Need at least $requiredPlayers active players to create a ${safeTeamSize}v${safeTeamSize} match. Currently active: ${presentPlayers.size}")
        }

        val playerMap = presentPlayers.associateBy { it.id }

        // Generate combinations of 4 players
        val player4Combinations = generateCombinations(presentPlayers, requiredPlayers)

        data class MatchCandidate(
            val players: List<Player>,
            val highestMatchCount: Int,
            val matchCountSpread: Int,
            val matchCountVariance: Long,
            val matchCountSum: Int,
            val recentRepeatPenalty: Int,
            val groupBonus: Int,
            val validTeamSplits: List<Pair<List<String>, List<String>>>
        )

        fun findCandidates(enforcedGroups: List<PlayerGroup>): List<MatchCandidate> {
            val candidates = mutableListOf<MatchCandidate>()

            for (fourPlayers in player4Combinations) {
                val fourIds = fourPlayers.map { it.id }.toSet()
                val matchCounts = fourPlayers.map { it.sessionMatchesPlayed }
                val lowestMatchCount = matchCounts.minOrNull() ?: 0
                val highestMatchCount = matchCounts.maxOrNull() ?: 0
                val matchCountSum = fourPlayers.sumOf { it.sessionMatchesPlayed }
                val averageCount = matchCountSum.toLong()
                val matchCountVariance = matchCounts.sumOf { count ->
                    val difference = count.toLong() * matchCounts.size - averageCount
                    difference * difference
                }

                var groupBonus = 0
                for (group in enforcedGroups) {
                    val groupPresentIds = group.playerIds.filter { playerMap.containsKey(it) }
                    if (groupPresentIds.size >= 2) {
                        val selectedInFour = groupPresentIds.filter { fourIds.contains(it) }
                        if (isSameMatchConstraint(group, groupPresentIds.size, safeTeamSize)) {
                            if (selectedInFour.size >= 2) {
                                groupBonus += 10
                            } else if (selectedInFour.size == 1 && groupPresentIds.size > selectedInFour.size) {
                                groupBonus -= 5
                            }
                        }
                    }
                }

                val selectedIds = fourPlayers.map { it.id }.toSet()
                val recentRepeatPenalty = recentLineupPenalty(selectedIds, recentHistory)
                val respectsSameMatchGroups = enforcedGroups.all { group ->
                    val groupPresentIds = group.playerIds.filter { playerMap.containsKey(it) }
                    if (!isSameMatchConstraint(group, groupPresentIds.size, safeTeamSize)) {
                        true
                    } else {
                        val selectedCount = groupPresentIds.count { it in selectedIds }
                        groupPresentIds.size > requiredPlayers ||
                            selectedCount == 0 || selectedCount == groupPresentIds.size
                    }
                }
                val validSplits = if (respectsSameMatchGroups) {
                    getValidTeamSplits(fourPlayers.map { it.id }, safeTeamSize, enforcedGroups)
                } else {
                    emptyList()
                }
                if (validSplits.isNotEmpty()) {
                    candidates.add(
                        MatchCandidate(
                            players = fourPlayers,
                            highestMatchCount = highestMatchCount,
                            matchCountSpread = highestMatchCount - lowestMatchCount,
                            matchCountVariance = matchCountVariance,
                            matchCountSum = matchCountSum,
                            recentRepeatPenalty = recentRepeatPenalty,
                            groupBonus = groupBonus,
                            validTeamSplits = validSplits
                        )
                    )
                }
            }
            return candidates
        }

        var enforcedGroups = groups
        var candidates = findCandidates(enforcedGroups)
        val ignoredGroups = mutableListOf<PlayerGroup>()
        while (candidates.isEmpty() && enforcedGroups.isNotEmpty()) {
            val ignoredGroup = enforcedGroups.random()
            ignoredGroups += ignoredGroup
            enforcedGroups = enforcedGroups - ignoredGroup
            candidates = findCandidates(enforcedGroups)
        }

        if (candidates.isEmpty()) {
            val fallback = getRelaxedTeamSplits(presentPlayers, safeTeamSize)
            if (fallback != null) {
                return MatchmakingResult.Success(fallback.first, "Match generated ignoring conflicting group constraints.")
            }
            return MatchmakingResult.Error("Could not generate match with active players.")
        }

        // Shuffle first to randomize exact ties safely without violating Timsort's comparator contract.
        // Fairness prioritizes total session appearances first, then protects against
        // one player being overused and finally compares spread and variance.
        val shuffledCandidates = candidates.shuffled()
        val sortedCandidates = shuffledCandidates.sortedWith(
            compareBy<MatchCandidate> { it.matchCountSum }
                .thenBy { it.highestMatchCount }
                .thenBy { it.matchCountSpread }
                .thenBy { it.matchCountVariance }
                .thenBy { it.recentRepeatPenalty }
                .thenByDescending { it.groupBonus }
        )
        val bestCandidate = sortedCandidates.first()
        val bestCandidates = sortedCandidates.filter {
            it.matchCountSum == bestCandidate.matchCountSum &&
            it.highestMatchCount == bestCandidate.highestMatchCount &&
                it.matchCountSpread == bestCandidate.matchCountSpread &&
                it.matchCountVariance == bestCandidate.matchCountVariance &&
                it.recentRepeatPenalty == bestCandidate.recentRepeatPenalty &&
                it.groupBonus == bestCandidate.groupBonus
        }
        val chosenCandidate = bestCandidates.random()
        val chosenSplit = chosenCandidate.validTeamSplits.random()

        val (team1, team2) = if (Random.nextBoolean()) {
            Pair(chosenSplit.first, chosenSplit.second)
        } else {
            Pair(chosenSplit.second, chosenSplit.first)
        }

        val match = AirsoftMatch(
            team1PlayerIds = team1,
            team2PlayerIds = team2
        )

        val message = if (ignoredGroups.isEmpty()) {
            null
        } else {
            "Conflicting rule(s) were randomly relaxed: ${ignoredGroups.joinToString { it.name }}."
        }
        return MatchmakingResult.Success(match, message)
    }

    private fun getValidTeamSplits(
        playerIds: List<String>,
        teamSize: Int,
        groups: List<PlayerGroup>
    ): List<Pair<List<String>, List<String>>> {
        val possibleSplits = mutableListOf<Pair<List<String>, List<String>>>()
        val team1Indexes = mutableListOf<Int>()
        fun buildSplits(start: Int) {
            if (team1Indexes.size == teamSize) {
                if (team1Indexes.contains(0)) {
                    val team1Set = team1Indexes.toSet()
                    val team1 = playerIds.filterIndexed { index, _ -> index in team1Set }
                    val team2 = playerIds.filterIndexed { index, _ -> index !in team1Set }
                    possibleSplits.add(team1 to team2)
                }
                return
            }
            for (index in start until playerIds.size) {
                team1Indexes.add(index)
                buildSplits(index + 1)
                team1Indexes.removeAt(team1Indexes.lastIndex)
            }
        }
        buildSplits(0)

        val matchSet = playerIds.toSet()
        val validSplits = mutableListOf<Pair<List<String>, List<String>>>()

        for (split in possibleSplits) {
            val team1Set = split.first.toSet()
            val team2Set = split.second.toSet()

            var isValid = true

            for (group in groups) {
                val membersInMatch = group.playerIds.filter { matchSet.contains(it) }
                if (membersInMatch.size >= 2) {
                    when (group.rule) {
                        GroupRule.SAME_TEAM -> {
                            if (membersInMatch.size <= teamSize) {
                                val inTeam1 = membersInMatch.all { team1Set.contains(it) }
                                val inTeam2 = membersInMatch.all { team2Set.contains(it) }
                                if (!inTeam1 && !inTeam2) {
                                    isValid = false
                                }
                            }
                        }
                        GroupRule.OPPOSING_TEAMS -> {
                            val inTeam1Count = membersInMatch.count { team1Set.contains(it) }
                            val inTeam2Count = membersInMatch.count { team2Set.contains(it) }
                            if (inTeam1Count == 0 || inTeam2Count == 0) {
                                isValid = false
                            }
                        }
                        GroupRule.SAME_MATCH -> {
                            // Handled in scoring
                        }
                    }
                }
                if (!isValid) break
            }

            if (isValid) {
                validSplits.add(split)
            }
        }

        return validSplits
    }

    private fun isSameMatchConstraint(
        group: PlayerGroup,
        presentMemberCount: Int,
        teamSize: Int
    ): Boolean {
        return group.rule == GroupRule.SAME_MATCH ||
            (group.rule == GroupRule.SAME_TEAM && presentMemberCount > teamSize)
    }

    private fun getRelaxedTeamSplits(
        presentPlayers: List<Player>,
        teamSize: Int
    ): Pair<AirsoftMatch, String>? {
        val requiredPlayers = teamSize * 2
        val selectedPlayers = presentPlayers.sortedBy { it.sessionMatchesPlayed }.take(requiredPlayers)
        if (selectedPlayers.size < requiredPlayers) return null
        val ids = selectedPlayers.map { it.id }.shuffled()
        val match = AirsoftMatch(
            team1PlayerIds = ids.take(teamSize),
            team2PlayerIds = ids.drop(teamSize)
        )
        return Pair(match, "Fallback match generated.")
    }

    private fun recentLineupPenalty(
        selectedIds: Set<String>,
        recentHistory: List<AirsoftMatch>
    ): Int {
        val recentMatches = recentHistory.take(10)
        return recentMatches.mapIndexed { index, historyMatch ->
            val historyIds = (historyMatch.team1PlayerIds + historyMatch.team2PlayerIds).toSet()
            if (historyIds == selectedIds) recentMatches.size - index else 0
        }.sum()
    }

    private fun <T> generateCombinations(list: List<T>, k: Int): List<List<T>> {
        val result = mutableListOf<List<T>>()
        fun helper(start: Int, current: MutableList<T>) {
            if (current.size == k) {
                result.add(ArrayList(current))
                return
            }
            for (i in start until list.size) {
                current.add(list[i])
                helper(i + 1, current)
                current.removeAt(current.size - 1)
            }
        }
        helper(0, mutableListOf())
        return result
    }

}
