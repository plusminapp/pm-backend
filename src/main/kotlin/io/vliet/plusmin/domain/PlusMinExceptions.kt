package io.vliet.plusmin.domain

import org.springframework.http.HttpStatus

/**
 * Custom exceptions for business logic violations
 */
sealed class PlusMinException(
    override val message: String,
    val httpStatus: HttpStatus,
    val errorCode: String,
    val parameters: List<String> = emptyList(),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class PM_PeriodeNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Periode met id $parameters[0] niet gevonden voor gebruiker $parameters[1]",
    HttpStatus.NOT_FOUND, "PERIODE_NOTFOUND", parameters
)

class PM_HulpvragerNotFoundException(
    message: String,
    val gebruikerId: String,
) : PlusMinException(message, HttpStatus.NOT_FOUND, "GEBRUIKER_NOTFOUND", listOf(gebruikerId))

class PM_RekeningNotFoundException(
    message: String,
    val rekeningNaam: String,
    val gebruikerNaam: String
) : PlusMinException(message, HttpStatus.NOT_FOUND, "REKENING_NOT_FOUND", listOf(rekeningNaam, gebruikerNaam))

class InsufficientBufferException(
    message: String,
    val gebruikerNaam: String
) : PlusMinException(message, HttpStatus.BAD_REQUEST, "INSUFFICIENT_BUFFER", listOf(gebruikerNaam))

class InvalidBetalingException(
    message: String,
    val details: String? = null
) : PlusMinException(message, HttpStatus.BAD_REQUEST, "INVALID_BETALING")

class PM_AuthorizationException(
    message: String,
    val aanvrager: String,
    val eigenaar: String,
) : PlusMinException(message, HttpStatus.FORBIDDEN, "AUTHORIZATION_FAILED", listOf(aanvrager, eigenaar))