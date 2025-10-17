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

// Authorization & Gebruiker excepties
class PM_GeneralAuthorizationException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "${parameters[0]} vraagt toegang tot ${parameters[1]} maar heeft daarvoor geen rechten.",
    HttpStatus.FORBIDDEN, "GENERAL_AUTHORIZATION_FAILED", parameters
)

class PM_CreateUserAuthorizationException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "${parameters[0]} wil nieuwe gebruikers ${parameters[1]} aanmaken maar is geen coördinator.",
    HttpStatus.FORBIDDEN, "CREATE_USER_AUTHORIZATION_FAILED", parameters
)

class PM_HulpvragerNotFoundException(
    parameters: List<String>
) : PlusMinException(
    "Hulpvrager met Id ${parameters[0]} bestaat niet.",
    HttpStatus.NOT_FOUND, "GEBRUIKER_NOTFOUND", parameters
)

// Periode exceptions
class PM_PeriodeNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Periode met id $parameters[0] niet gevonden.",
    HttpStatus.NOT_FOUND, "PERIODE_NOT_FOUND", parameters
)

class PM_LaatsteGeslotenPeriodeNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Er is geen laatst gesloten of opgeruimde periode voor gebruiker ${parameters[0]}",
    HttpStatus.NOT_FOUND, "LAATSTE_PERIODE_NOT_FOUND", parameters
)

class PM_HuidigePeriodeNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Geen huidige periode gevonden voor gebruiker $parameters[1]",
    HttpStatus.NOT_FOUND, "HUIDIGE_PERIODE_NOT_FOUND", parameters
)

class PM_NoOpenPeriodException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Op ${parameters[0]} is er geen OPEN periode voor ${parameters[1]}.",
    HttpStatus.BAD_REQUEST, "GEEN_OPEN_PERIODE", parameters
)

class PM_VorigePeriodeNietGeslotenException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Periode ${parameters[0]} kan niet worden gesloten/gewijzigd, de vorige periode ${parameters[1]} is niet gesloten voor gebruiker ${parameters[2]}",
        HttpStatus.BAD_REQUEST, "VORIGE_PERIODE_NIET_GESLOTEN", parameters
)

class PM_PeriodeNietOpenException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Periode ${parameters[0]} kan niet worden gesloten/gewijzigd, de periode is niet open voor gebruiker ${parameters[1]}",
        HttpStatus.BAD_REQUEST, "PERIODE_NIET_OPEN", parameters
)

class PM_PeriodeNietGeslotenException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Periode ${parameters[0]} kan niet worden ${parameters[1]}, de periode is niet gesloten voor gebruiker ${parameters[2]}",
        HttpStatus.BAD_REQUEST, "PERIODE_NIET_GESLOTEN", parameters
)

class PM_PeriodeNietLaatstGeslotenException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Periode ${parameters[0]} kan niet worden heropend, het is niet de laatst gesloten periode (dat is periode ${parameters[1]}) voor gebruiker ${parameters[2]}",
        HttpStatus.BAD_REQUEST, "PERIODE_NIET_LAATST_GESLOTEN", parameters
)

// Rekening excepties
class PM_RekeningNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Rekening ${parameters[0]} bestaat niet voor ${parameters[1]}.",
    HttpStatus.NOT_FOUND, "REKENING_NOT_FOUND", parameters
)

class PM_RekeningNotLinkedException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "${parameters[0]} heeft geen gekoppelde rekening voor ${parameters[1]}.",
    HttpStatus.INTERNAL_SERVER_ERROR, "REKENING_NOT_LINKED", parameters
)

class PM_BufferRekeningNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Buffer rekening niet gevonden voor ${parameters[0]}.",
    HttpStatus.INTERNAL_SERVER_ERROR, "BUFFER_REKENING_NOT_FOUND", parameters
)

class PM_SpaarRekeningNotFoundException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Geen spaarrekening gevonden voor ${parameters[0]}.",
    HttpStatus.INTERNAL_SERVER_ERROR, "SPAAR_REKENING_NOT_FOUND", parameters
)

class PM_GeenBetaaldagException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Geen budgetBetaalDag voor ${parameters[0]} met BudgetType ${parameters[1]} van ${parameters[2]}",
    HttpStatus.INTERNAL_SERVER_ERROR, "BUFFER_REKENING_NOT_FOUND", parameters
)

class PM_BufferRekeningImmutableException(
) : PlusMinException(
    "RekeningGroep met soort RESERVERING_BUFFER mag niet handmatig worden aangemaakt of aangepast.",
    HttpStatus.INTERNAL_SERVER_ERROR, "BUFFER_REKENING_IMMUTABLE"
)

class PM_PotjeMoetGekoppeldeRekeningException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Potjes rekening ${parameters[0]} moet gekoppeld zijn aan een betaalmiddel.",
    HttpStatus.INTERNAL_SERVER_ERROR, "POTJE_MOET_GEKOPPELDE_REKENING", parameters
)

class PM_RekeningMoetBetaalmethodeException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Rekening ${parameters[0]} moet geldige betaalmethoden hebben.",
    HttpStatus.INTERNAL_SERVER_ERROR, "POTJE_MOET_BETAALMETHODE", parameters
)

// Saldo excepties
class PM_OnvoldoendeBufferSaldoException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Buffer (${parameters[0]}) te laag bij start van periode ${parameters[1]} voor ${parameters[2]}: reserveringstekorten ${parameters[3]}.",
    HttpStatus.BAD_REQUEST, "ONVOLDOENDE_BUFFER", parameters
)

class PM_GeenSaldoVoorRekeningException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Geen Saldo voor ${parameters[0]} voor ${parameters[1]}.",
    HttpStatus.INTERNAL_SERVER_ERROR, "GEEN_SALDO_VOOR_REKENING", parameters
)

class PM_GeenPeriodeVoorSaldoException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "Geen Periode bij Saldo ${parameters[0]} voor ${parameters[1]}.",
    HttpStatus.INTERNAL_SERVER_ERROR, "GEEN_PERIODE_VOOR_SALDO", parameters
)

class PM_GeenBufferVoorSaldoException(
    parameters: List<String> = emptyList()
) : PlusMinException(
    "RESERVERING_BUFFER Saldo voor periode ${parameters[0]} bestaat niet voor ${parameters[1]}.",
    HttpStatus.INTERNAL_SERVER_ERROR, "GEEN_BUFFER_VOOR_SALDO", parameters
)
