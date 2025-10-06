package io.vliet.plusmin.domain

import java.time.LocalDateTime

data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val parameters: List<String> = emptyList(),
    val path: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
