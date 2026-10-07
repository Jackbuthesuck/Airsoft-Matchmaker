package com.wazuhonde.asfield.model

import java.util.UUID

data class ActivityEvent(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val subjectId: String? = null,
    val kind: String? = null
)
