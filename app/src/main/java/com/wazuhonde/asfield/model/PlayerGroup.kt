package com.wazuhonde.asfield.model

import java.util.UUID

data class PlayerGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var playerIds: List<String>,
    var rule: GroupRule
)
