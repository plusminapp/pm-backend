package io.vliet.plusmin.controller

import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.ReserveringService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/reserveringen")
class ReserveringController {
    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var reserveringService: ReserveringService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @PutMapping("/administratie/{administratieId}/periode/{periodeId}")
    fun creeerReserveringVoorPeriode(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorPeriode voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        reserveringService.creeerReserveringenVoorPeriode(administratie, periodeId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/administratie/{administratieId}")
    fun creeerReservering(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorPeriode voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        reserveringService.creeerReserveringen(administratie)
        return ResponseEntity.ok().build()
    }
}
