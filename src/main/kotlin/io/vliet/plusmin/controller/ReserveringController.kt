package io.vliet.plusmin.controller

import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.ReserveringService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/reserveringen")
class ReserveringController {
    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var reserveringService: ReserveringService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @PutMapping("/administratie/{administratieId}/alle")
    fun creeerAlleReservering(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT ReserveringController.creeerAlleReservering voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        reserveringService.creeerAlleReserveringen(administratie)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/administratie/{administratieId}")
    fun creeerReservering(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT ReserveringController.creeerReservering voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        reserveringService.creeerReserveringen(administratie)
        return ResponseEntity.ok().build()
    }
    @PutMapping("/administratie/{administratieId}/updateBufferSaldo")
    fun updateBufferSaldo(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT ReserveringController.updateBufferSaldo voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        reserveringService.updateOpeningsReserveringsSaldo(administratie)
        return ResponseEntity.ok().build()
    }
}
