package io.vliet.plusmin.controller

import io.vliet.plusmin.service.ReserveringService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/reserveringen")
class ReserveringController {
    @Autowired
    lateinit var gebruikerController: GebruikerController
    
    @Autowired
    lateinit var reserveringService: ReserveringService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<String> {
        val stackTraceElement = ex.stackTrace.firstOrNull { it.className.startsWith("io.vliet") }
            ?: ex.stackTrace.firstOrNull()
        val locationInfo = stackTraceElement?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
        val errorMessage = "${ex.message}$locationInfo"
        logger.error(errorMessage)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage)
    }

//    @PostMapping("/hulpvrager/{hulpvragerId}")
//    fun creeerNieuweReserveringVoorHulpvrager(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @Valid @RequestBody reserveringDTO: ReserveringDTO,
//    ): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("POST ReserveringController.creeerNieuweReserveringVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        val reservering = reserveringService.creeerReservering(hulpvrager, reserveringDTO)
//        return ResponseEntity.ok().body(reservering)
//    }
//
//    @PostMapping("/hulpvrager/{hulpvragerId}/list")
//    fun creeerNieuweReserveringenVoorHulpvrager(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @Valid @RequestBody reserveringDTO: List<ReserveringDTO>,
//    ): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("POST ReserveringController.creeerNieuweReserveringVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        val reservering = reserveringService.creeerReserveringen(hulpvrager, reserveringDTO)
//        return ResponseEntity.ok().body(reservering)
//    }

    @PostMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun creeerNieuweReserveringVoorPeriode(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorPeriode voor ${hulpvrager.email} door ${vrijwilliger.email}")
        reserveringService.creeerReservingen(hulpvrager)
        return ResponseEntity.ok().body("Reserveringen aangemaakt voor de huidige periode voor ${hulpvrager.email}.")
    }

//    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}/budgethorizon")
//    fun getBudgetHorizon(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @PathVariable("periodeId") periodeId: Long,
//    ): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("POST ReserveringController.creeerNieuweReserveringVoorPeriode voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        val periode = reserveringService.periodeRepository.findById(periodeId).getOrNull()
//            ?: throw IllegalStateException("Periode met id $periodeId bestaat niet.")
//        if (periode.gebruiker.id != hulpvrager.id) {
//            throw IllegalStateException("Periode met id $periodeId hoort niet bij hulpvrager ${hulpvrager.email}.")
//        }
//        val (reserveringHorizon, budgetHorizon) = cashflowService.getReserveringEnBudgetHorizon(hulpvrager, periode)
//        return ResponseEntity.ok().body("Budgethorizon ${budgetHorizon} en reserveringHorizon ${reserveringHorizon} berekend voor periode ${periode.periodeStartDatum} t/m ${periode.periodeEindDatum} voor ${hulpvrager.email}.")
//    }

//    @PostMapping("/hulpvrager/{hulpvragerId}/periodes")
//    fun creeerNieuweReserveringVoorAllePeriodes(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//    ): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("POST ReserveringController.creeerNieuweReserveringVoorAllePeriodes voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        reserveringService.creeerReservingenVoorAllePeriodes(hulpvrager)
//        return ResponseEntity.ok().body("Reserveringen aangemaakt voor alle periodes voor ${hulpvrager.email}.")
//    }

//    @PostMapping("/hulpvrager/{hulpvragerId}/spaarRekening/{spaarRekeningNaam}")
//    fun creeerReserveringenVoorStortenSpaargeld(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @PathVariable("spaarRekeningNaam") spaarRekeningNaam: String,
//    ): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("POST ReserveringController.creeerReserveringenVoorStortenSpaargeld voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        reserveringService.creeerReserveringenVoorStortenSpaargeld(hulpvrager, spaarRekeningNaam)
//        return ResponseEntity.ok().body("Reserveringen aangemaakt voor alle periodes voor ${hulpvrager.email}.")
//    }

//    @GetMapping("/hulpvrager/{hulpvragerId}/datum/{datum}")
//    fun getReserveringenEnBetalingenVoorHulpvrager(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @PathVariable("datum") datum: String, // Datum in ISO-8601 formaat (yyyy-MM-dd)
//    ): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("GET ReserveringController.getReserveringenEnBetalingenVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        return ResponseEntity.ok().body(reserveringService
//            .getReserveringenEnBetalingenVoorHulpvrager(hulpvrager, LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE))
//            .mapKeys { it.key?.naam ?: "Onbekend" }
//        )
//    }
}
