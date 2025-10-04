package io.vliet.plusmin.domain

/**
 * Custom exceptions for business logic violations
 */
sealed class PlusMinException(
    override val message: String,
    val errorCode: String,
    val parameters: List<String> = emptyList(),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class InvalidPeriodeException(
    message: String,
    val gebruikerNaam: String
) : PlusMinException(message, "INVALID_PERIODE", listOf(gebruikerNaam))

class RekeningNotFoundException(
    message: String,
    val rekeningNaam: String,
    val gebruikerNaam: String
) : PlusMinException(message, "REKENING_NOT_FOUND", listOf(rekeningNaam, gebruikerNaam))

class InsufficientBufferException(
    message: String,
    val gebruikerNaam: String
) : PlusMinException(message, "INSUFFICIENT_BUFFER", listOf(gebruikerNaam))

class InvalidBetalingException(
    message: String,
    val details: String? = null
) : PlusMinException(message, "INVALID_BETALING")

class DuplicateResourceException(
    message: String,
    val resourceType: String,
    val resourceId: String
) : PlusMinException(message, "DUPLICATE_RESOURCE")

class AuthorizationException(
    message: String,
    val requestedResource: String
) : PlusMinException(message, "AUTHORIZATION_FAILED")