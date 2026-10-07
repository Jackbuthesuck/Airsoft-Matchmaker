package com.wazuhonde.asfield

import com.wazuhonde.asfield.data.Matchmaker
import com.wazuhonde.asfield.model.GroupRule
import com.wazuhonde.asfield.model.Player
import com.wazuhonde.asfield.model.PlayerGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchmakerTest {

    private val matchmaker = Matchmaker()

    @Test
    fun testInsufficientPlayersReturnsError() {
        val players = listOf(
            Player(id = "1", name = "P1"),
            Player(id = "2", name = "P2"),
            Player(id = "3", name = "P3")
        )
        val result = matchmaker.generate2v2Match(players, emptyList())
        assertTrue(result is Matchmaker.MatchmakingResult.Error)
    }

    @Test
    fun testSpreadPlayEquallyFavorsPlayersWithFewerMatches() {
        val players = listOf(
            Player(id = "1", name = "P1", sessionMatchesPlayed = 10),
            Player(id = "2", name = "P2", sessionMatchesPlayed = 0),
            Player(id = "3", name = "P3", sessionMatchesPlayed = 0),
            Player(id = "4", name = "P4", sessionMatchesPlayed = 0),
            Player(id = "5", name = "P5", sessionMatchesPlayed = 0)
        )
        val result = matchmaker.generate2v2Match(players, emptyList())
        assertTrue(result is Matchmaker.MatchmakingResult.Success)

        val match = (result as Matchmaker.MatchmakingResult.Success).match
        val allSelected = match.team1PlayerIds + match.team2PlayerIds

        assertTrue(!allSelected.contains("1"))
        assertEquals(4, allSelected.size)
    }

    @Test
    fun testSameTeamGroupConstraint() {
        val p1 = Player(id = "1", name = "P1", matchesPlayed = 0)
        val p2 = Player(id = "2", name = "P2", matchesPlayed = 0)
        val p3 = Player(id = "3", name = "P3", matchesPlayed = 0)
        val p4 = Player(id = "4", name = "P4", matchesPlayed = 0)
        val players = listOf(p1, p2, p3, p4)

        val sameTeamGroup = PlayerGroup(
            name = "Duo 1",
            playerIds = listOf("1", "2"),
            rule = GroupRule.SAME_TEAM
        )

        val result = matchmaker.generate2v2Match(players, listOf(sameTeamGroup))
        assertTrue(result is Matchmaker.MatchmakingResult.Success)

        val match = (result as Matchmaker.MatchmakingResult.Success).match
        val team1 = match.team1PlayerIds.toSet()
        val team2 = match.team2PlayerIds.toSet()

        val p1AndP2Together = (team1.contains("1") && team1.contains("2")) ||
                (team2.contains("1") && team2.contains("2"))
        assertTrue("P1 and P2 must be on the same team", p1AndP2Together)
    }

    @Test
    fun testOpposingTeamsGroupConstraint() {
        val p1 = Player(id = "1", name = "P1", matchesPlayed = 0)
        val p2 = Player(id = "2", name = "P2", matchesPlayed = 0)
        val p3 = Player(id = "3", name = "P3", matchesPlayed = 0)
        val p4 = Player(id = "4", name = "P4", matchesPlayed = 0)
        val players = listOf(p1, p2, p3, p4)

        val opposingGroup = PlayerGroup(
            name = "Rivals",
            playerIds = listOf("1", "2"),
            rule = GroupRule.OPPOSING_TEAMS
        )

        val result = matchmaker.generate2v2Match(players, listOf(opposingGroup))
        assertTrue(result is Matchmaker.MatchmakingResult.Success)

        val match = (result as Matchmaker.MatchmakingResult.Success).match
        val team1 = match.team1PlayerIds.toSet()
        val team2 = match.team2PlayerIds.toSet()

        val onOpposing = (team1.contains("1") && team2.contains("2")) ||
                (team1.contains("2") && team2.contains("1"))
        assertTrue("P1 and P2 must be on opposing teams", onOpposing)
    }

    @Test
    fun testLowestSessionTotalBeatsEvenButAlreadyPlayedPair() {
        val players = listOf(
            Player(id = "A", name = "A", sessionMatchesPlayed = 1),
            Player(id = "B", name = "B", sessionMatchesPlayed = 1),
            Player(id = "C", name = "C", sessionMatchesPlayed = 0)
        )
        val opposingGroup = PlayerGroup(
            name = "Opponents",
            playerIds = listOf("A", "B"),
            rule = GroupRule.OPPOSING_TEAMS
        )

        val result = matchmaker.generate2v2Match(
            allPlayers = players,
            groups = listOf(opposingGroup),
            teamSize = 1
        )

        assertTrue(result is Matchmaker.MatchmakingResult.Success)
        val selected = (result as Matchmaker.MatchmakingResult.Success).match
            .let { it.team1PlayerIds + it.team2PlayerIds }
        assertTrue("The unused player should be selected", selected.contains("C"))
    }

    @Test
    fun testStackedSameMatchAndOpposingRules() {
        val players = listOf(
            Player(id = "A", name = "A"),
            Player(id = "B", name = "B"),
            Player(id = "C", name = "C"),
            Player(id = "D", name = "D")
        )
        val sameMatch = PlayerGroup(
            name = "Same game",
            playerIds = listOf("A", "B", "C"),
            rule = GroupRule.SAME_MATCH
        )
        val opposing = PlayerGroup(
            name = "Opponents",
            playerIds = listOf("A", "D"),
            rule = GroupRule.OPPOSING_TEAMS
        )

        val result = matchmaker.generate2v2Match(players, listOf(sameMatch, opposing))
        assertTrue(result is Matchmaker.MatchmakingResult.Success)

        val match = (result as Matchmaker.MatchmakingResult.Success).match
        val selected = (match.team1PlayerIds + match.team2PlayerIds).toSet()
        assertEquals(setOf("A", "B", "C", "D"), selected)
        assertTrue(
            (match.team1PlayerIds.contains("A") && match.team2PlayerIds.contains("D")) ||
                (match.team1PlayerIds.contains("D") && match.team2PlayerIds.contains("A"))
        )
    }
}
