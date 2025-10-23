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

    @PostMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun creeerReserveringVoorPeriode(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorPeriode voor ${hulpvrager.email} door ${vrijwilliger.email}")
        reserveringService.creeerReserveringenVoorPeriode(hulpvrager, periodeId)
        return ResponseEntity.ok().body("Reserveringen aangemaakt voor de periode ${periodeId} voor ${hulpvrager.email}.")
    }

    @PostMapping("/hulpvrager/{hulpvragerId}")
    fun creeerReservering(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("POST ReserveringController.creeerNieuweReserveringVoorPeriode voor ${hulpvrager.email} door ${vrijwilliger.email}")
        reserveringService.creeerReserveringen(hulpvrager)
        return ResponseEntity.ok().body("Reserveringen aangemaakt voor alle periode voor ${hulpvrager.email}.")
    }
}
