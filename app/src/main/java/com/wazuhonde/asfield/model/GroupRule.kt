package com.wazuhonde.asfield.model

enum class GroupRule(val displayName: String, val description: String) {
    SAME_TEAM("Same Team", "Members play on the same team"),
    SAME_MATCH("Same Match", "Members play in the same 4-player match"),
    OPPOSING_TEAMS("Opposing Teams", "Members play on opposing teams")
}
