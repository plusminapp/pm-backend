package io.vliet.plusmin.domain

import java.time.LocalDateTime

data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val parameters: List<String> = emptyList(),
    val path: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)

data class BusinessRuleException(
    override val message: String,
    val code: String = "BUSINESS_RULE_VIOLATION"
) : RuntimeException(message)

data class ResourceNotFoundException(
    override val message: String,
    val resourceType: String,
    val resourceId: Any
) : RuntimeException(message) {
    val code: String = "RESOURCE_NOT_FOUND"
}