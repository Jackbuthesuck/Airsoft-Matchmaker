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

    /**
     * Creates a match in four steps: choose available players, find valid lineups,
     * rank those lineups for fairness, then randomly split the chosen lineup into teams.
     */
    fun generateMatch(
        allPlayers: List<Player>,
        groups: List<PlayerGroup>,
        teamSize: Int = 2,
        excludedPlayerIds: Set<String> = emptySet(),
        recentHistory: List<AirsoftMatch> = emptyList()
    ): MatchmakingResult {
        val safeTeamSize = teamSize.coerceIn(1, 4)
        val requiredPlayerCount = safeTeamSize * 2
        val availablePlayers = chooseAvailablePlayers(
            allPlayers,
            excludedPlayerIds,
            requiredPlayerCount
        )

        if (availablePlayers.size < requiredPlayerCount) {
            return MatchmakingResult.Error(
                "Need at least $requiredPlayerCount active players to create a ${safeTeamSize}v${safeTeamSize} match. Currently active: ${availablePlayers.size}"
            )
        }

        val searchResult = findCandidatesWithRelaxedGroups(
            availablePlayers = availablePlayers,
            groups = groups,
            teamSize = safeTeamSize,
            recentHistory = recentHistory
        )

        if (searchResult.candidates.isEmpty()) {
            val fallbackMatch = createFallbackMatch(availablePlayers, safeTeamSize)
                ?: return MatchmakingResult.Error("Could not generate match with active players.")
            return MatchmakingResult.Success(
                fallbackMatch,
                "Match generated ignoring conflicting group constraints."
            )
        }

        val chosenCandidate = chooseFairestCandidate(searchResult.candidates)
        val chosenSplit = chosenCandidate.validTeamSplits.random()
        val match = randomizeTeamSides(chosenSplit)
        val message = searchResult.ignoredGroups
            .takeIf { it.isNotEmpty() }
            ?.let { "Conflicting rule(s) were randomly relaxed: ${it.joinToString { group -> group.name }}." }

        return MatchmakingResult.Success(match, message)
    }

    /** Players from the current match are skipped when enough other active players exist. */
    private fun chooseAvailablePlayers(
        allPlayers: List<Player>,
        excludedPlayerIds: Set<String>,
        requiredPlayerCount: Int
    ): List<Player> {
        val activePlayers = allPlayers.filter { it.isPresent }
        val playersNotInCurrentMatch = activePlayers.filterNot { it.id in excludedPlayerIds }
        return if (playersNotInCurrentMatch.size >= requiredPlayerCount) {
            playersNotInCurrentMatch
        } else {
            activePlayers
        }
    }

    /** A candidate is one possible lineup, along with its fairness score and valid team splits. */
    private data class MatchCandidate(
        val highestMatchCount: Int,
        val matchCountSpread: Int,
        val matchCountVariance: Long,
        val matchCountSum: Int,
        val recentRepeatPenalty: Int,
        val groupBonus: Int,
        val validTeamSplits: List<Pair<List<String>, List<String>>>
    )

    private data class CandidateSearchResult(
        val candidates: List<MatchCandidate>,
        val ignoredGroups: List<PlayerGroup>
    )

    /** Retry the lineup search, dropping one random group rule each time it finds no solution. */
    private fun findCandidatesWithRelaxedGroups(
        availablePlayers: List<Player>,
        groups: List<PlayerGroup>,
        teamSize: Int,
        recentHistory: List<AirsoftMatch>
    ): CandidateSearchResult {
        var enforcedGroups = groups
        var candidates = findCandidates(availablePlayers, enforcedGroups, teamSize, recentHistory)
        val ignoredGroups = mutableListOf<PlayerGroup>()

        while (candidates.isEmpty() && enforcedGroups.isNotEmpty()) {
            val groupToIgnore = enforcedGroups.random()
            ignoredGroups += groupToIgnore
            enforcedGroups = enforcedGroups - groupToIgnore
            candidates = findCandidates(availablePlayers, enforcedGroups, teamSize, recentHistory)
        }

        return CandidateSearchResult(candidates, ignoredGroups)
    }

    /** Try every possible lineup, keeping only those that can be split under the active rules. */
    private fun findCandidates(
        players: List<Player>,
        groups: List<PlayerGroup>,
        teamSize: Int,
        recentHistory: List<AirsoftMatch>
    ): List<MatchCandidate> {
        val playerById = players.associateBy { it.id }
        val requiredPlayerCount = teamSize * 2

        return generateCombinations(players, requiredPlayerCount).mapNotNull { lineup ->
            val playerIds = lineup.map { it.id }
            val lineupIds = playerIds.toSet()

            if (!includesWholeSameMatchGroups(lineupIds, playerById, groups, requiredPlayerCount, teamSize)) {
                return@mapNotNull null
            }

            val validSplits = getValidTeamSplits(playerIds, teamSize, groups)
            if (validSplits.isEmpty()) return@mapNotNull null

            val counts = lineup.map { it.sessionMatchesPlayed }
            MatchCandidate(
                highestMatchCount = counts.maxOrNull() ?: 0,
                matchCountSpread = (counts.maxOrNull() ?: 0) - (counts.minOrNull() ?: 0),
                matchCountVariance = calculateMatchCountVariance(counts),
                matchCountSum = counts.sum(),
                recentRepeatPenalty = recentLineupPenalty(lineupIds, recentHistory),
                groupBonus = calculateGroupBonus(lineupIds, playerById, groups, teamSize),
                validTeamSplits = validSplits
            )
        }
    }

    /** A Same Match rule cannot select only part of a group that fits in the lineup. */
    private fun includesWholeSameMatchGroups(
        lineupIds: Set<String>,
        playerById: Map<String, Player>,
        groups: List<PlayerGroup>,
        requiredPlayerCount: Int,
        teamSize: Int
    ): Boolean {
        return groups.all { group ->
            val presentIds = group.playerIds.filter { it in playerById }
            if (!isSameMatchConstraint(group, presentIds.size, teamSize)) {
                true
            } else {
                val selectedCount = presentIds.count { it in lineupIds }
                presentIds.size > requiredPlayerCount || selectedCount == 0 || selectedCount == presentIds.size
            }
        }
    }

    /** Groups add a small tie-breaking preference when multiple group members make the lineup. */
    private fun calculateGroupBonus(
        lineupIds: Set<String>,
        playerById: Map<String, Player>,
        groups: List<PlayerGroup>,
        teamSize: Int
    ): Int {
        var bonus = 0
        for (group in groups) {
            val presentIds = group.playerIds.filter { it in playerById }
            if (presentIds.size < 2 || !isSameMatchConstraint(group, presentIds.size, teamSize)) continue

            val selectedCount = presentIds.count { it in lineupIds }
            bonus += when {
                selectedCount >= 2 -> 10
                selectedCount == 1 && presentIds.size > selectedCount -> -5
                else -> 0
            }
        }
        return bonus
    }

    /** Scale the counts before comparing them so the variance stays an integer. */
    private fun calculateMatchCountVariance(counts: List<Int>): Long {
        val scaledAverage = counts.sum().toLong()
        return counts.sumOf { count ->
            val difference = count.toLong() * counts.size - scaledAverage
            difference * difference
        }
    }

    /** Lower scores mean a fairer lineup. Exact ties are selected randomly. */
    private fun chooseFairestCandidate(candidates: List<MatchCandidate>): MatchCandidate {
        val rankedCandidates = candidates.shuffled().sortedWith(
            compareBy<MatchCandidate> { it.matchCountSum }
                .thenBy { it.highestMatchCount }
                .thenBy { it.matchCountSpread }
                .thenBy { it.matchCountVariance }
                .thenBy { it.recentRepeatPenalty }
                .thenByDescending { it.groupBonus }
        )
        val bestScore = rankedCandidates.first()
        return rankedCandidates.filter { it.hasSameScoreAs(bestScore) }.random()
    }

    private fun MatchCandidate.hasSameScoreAs(other: MatchCandidate): Boolean {
        return matchCountSum == other.matchCountSum &&
            highestMatchCount == other.highestMatchCount &&
            matchCountSpread == other.matchCountSpread &&
            matchCountVariance == other.matchCountVariance &&
            recentRepeatPenalty == other.recentRepeatPenalty &&
            groupBonus == other.groupBonus
    }

    /** Randomly choose which valid split is used and which side is called Team 1. */
    private fun randomizeTeamSides(split: Pair<List<String>, List<String>>): AirsoftMatch {
        val (teamOne, teamTwo) = if (Random.nextBoolean()) split else split.second to split.first
        return AirsoftMatch(team1PlayerIds = teamOne, team2PlayerIds = teamTwo)
    }

    /** Generate unique team splits, then discard splits that break a group rule. */
    private fun getValidTeamSplits(
        playerIds: List<String>,
        teamSize: Int,
        groups: List<PlayerGroup>
    ): List<Pair<List<String>, List<String>>> {
        val possibleSplits = generateCombinations(playerIds.indices.toList(), teamSize)
            // Team 1 always contains the first player to avoid checking mirrored copies.
            .filter { 0 in it }
            .map { teamOneIndexes ->
                val teamOneIndexSet = teamOneIndexes.toSet()
                playerIds.filterIndexed { index, _ -> index in teamOneIndexSet } to
                    playerIds.filterIndexed { index, _ -> index !in teamOneIndexSet }
            }
        val lineupIds = playerIds.toSet()

        return possibleSplits.filter { (teamOne, teamTwo) ->
            val teamOneIds = teamOne.toSet()
            val teamTwoIds = teamTwo.toSet()
            groups.all { group ->
                val membersInLineup = group.playerIds.filter { it in lineupIds }
                if (membersInLineup.size < 2) return@all true

                when (group.rule) {
                    GroupRule.SAME_TEAM -> membersInLineup.size > teamSize ||
                        membersInLineup.all { it in teamOneIds } || membersInLineup.all { it in teamTwoIds }
                    GroupRule.OPPOSING_TEAMS -> membersInLineup.any { it in teamOneIds } &&
                        membersInLineup.any { it in teamTwoIds }
                    GroupRule.SAME_MATCH -> true // Checked while choosing the lineup.
                }
            }
        }
    }

    private fun isSameMatchConstraint(
        group: PlayerGroup,
        presentMemberCount: Int,
        teamSize: Int
    ): Boolean {
        return group.rule == GroupRule.SAME_MATCH ||
            (group.rule == GroupRule.SAME_TEAM && presentMemberCount > teamSize)
    }

    /** Last resort: make a fair lineup and split it without applying group rules. */
    private fun createFallbackMatch(players: List<Player>, teamSize: Int): AirsoftMatch? {
        val selectedPlayers = players.sortedBy { it.sessionMatchesPlayed }.take(teamSize * 2)
        if (selectedPlayers.size < teamSize * 2) return null

        val shuffledIds = selectedPlayers.map { it.id }.shuffled()
        return AirsoftMatch(
            team1PlayerIds = shuffledIds.take(teamSize),
            team2PlayerIds = shuffledIds.drop(teamSize)
        )
    }

    /** Penalize an exact repeat of any of the ten most recent lineups. */
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

    /** List every distinct group of k items selected from the input list. */
    private fun <T> generateCombinations(items: List<T>, chooseCount: Int): List<List<T>> {
        val combinations = mutableListOf<List<T>>()

        fun addCombinations(startIndex: Int, current: MutableList<T>) {
            if (current.size == chooseCount) {
                combinations.add(current.toList())
                return
            }
            for (index in startIndex until items.size) {
                current.add(items[index])
                addCombinations(index + 1, current)
                current.removeAt(current.lastIndex)
            }
        }

        addCombinations(0, mutableListOf())
        return combinations
    }
}
