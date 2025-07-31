package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Reservering
import io.vliet.plusmin.domain.Reservering.ReserveringDTO
import io.vliet.plusmin.repository.ReserveringRepository
import io.vliet.plusmin.service.ReserveringService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull


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

    @PostMapping("/hulpvrager/{hulpvragerId}")
    fun creeerNieuweReserveringVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @Valid @RequestBody reserveringDTO: ReserveringDTO,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val reservering = reserveringService.creeerReservering(hulpvrager, reserveringDTO)
        return ResponseEntity.ok().body(reservering)
    }

    @PostMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun creeerNieuweReserveringVoorPeriode(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val periode = reserveringService.periodeRepository.findById(periodeId).getOrNull()
            ?: throw IllegalStateException("Periode met id $periodeId bestaat niet.")
        if (periode.gebruiker.id != hulpvrager.id) {
            throw IllegalStateException("Periode met id $periodeId hoort niet bij hulpvrager ${hulpvrager.email}.")
        }
        reserveringService.creeerReservingenVoorPeriode(periode)
        return ResponseEntity.ok().body("Reserveringen aangemaakt voor periode ${periode.periodeStartDatum} t/m ${periode.periodeEindDatum} voor ${hulpvrager.email}.")
    }

    @PostMapping("/hulpvrager/{hulpvragerId}/periodes")
    fun creeerNieuweReserveringVoorAllePeriodes(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        reserveringService.creeerReservingenVoorAllePeriodes(hulpvrager)
        return ResponseEntity.ok().body("Reserveringen aangemaakt voor alle periodes voor ${hulpvrager.email}.")
    }

    @GetMapping("/hulpvrager/{hulpvragerId}")
    fun getReserveringenVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT BetalingController.getDatumLaatsteBetaling voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(reserveringService
            .getReserveringenVoorHulpvrager(hulpvrager)
            .mapKeys { it.key?.naam ?: "Onbekend" }
        )
    }
}
