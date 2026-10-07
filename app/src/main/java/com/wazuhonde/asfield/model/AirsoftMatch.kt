package com.wazuhonde.asfield.model

import java.util.UUID

enum class MatchResult {
    NONE, TEAM1_WIN, TEAM2_WIN, DRAW
}

data class AirsoftMatch(
    val id: String = UUID.randomUUID().toString(),
    val team1PlayerIds: List<String>,
    val team2PlayerIds: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
    var result: MatchResult = MatchResult.NONE
)
