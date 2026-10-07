package com.wazuhonde.asfield.model

import java.util.UUID

data class Player(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var isPresent: Boolean = true,
    var matchesPlayed: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var draws: Int = 0,
    var statsDayKey: String? = null,
    var dayMatchesPlayed: Int = 0,
    var dayWins: Int = 0,
    var dayLosses: Int = 0,
    var dayDraws: Int = 0,
    var statsWeekKey: String? = null,
    var weekMatchesPlayed: Int = 0,
    var weekWins: Int = 0,
    var weekLosses: Int = 0,
    var weekDraws: Int = 0,
    var sessionMatchesPlayed: Int = 0,
    var sessionWins: Int = 0,
    var sessionLosses: Int = 0,
    var sessionDraws: Int = 0
)
