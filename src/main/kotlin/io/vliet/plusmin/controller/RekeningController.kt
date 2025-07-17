package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.service.RekeningCashflowService
import io.vliet.plusmin.service.RekeningService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.jvm.optionals.getOrElse

@RestController
@RequestMapping("/rekening")
class RekeningController {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var rekeningCashflowService: RekeningCashflowService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerController: GebruikerController

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    // Iedereen mag de eigen rekening opvragen
    @Operation(summary = "GET de eigen rekeninggroepen op basis van de JWT")
    @GetMapping("")
    fun findRekeningGroepen(): List<RekeningGroep> {
        val gebruiker = gebruikerController.getJwtGebruiker()
        logger.info("GET RekeningController.findRekeningen() voor gebruiker ${gebruiker.email}.")
        return rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
    }

    @Operation(summary = "GET de geldige rekeningen in een periode")
    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun getAlleRekeningenPerBetalingsSoortVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET RekeningController.getAlleRekeningenPerBetalingsSoortVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val periode = periodeRepository.findById(periodeId)
            .getOrElse { return ResponseEntity.notFound().build() }
        return ResponseEntity.ok().body(rekeningService.rekeningGroepenPerBetalingsSoort(hulpvrager, periode))
    }

    @Operation(summary = "GET de geldige rekeningen in een periode")
    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}/cashflow")
    fun getCashflowVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET RekeningController.getCashflowVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val periode = periodeRepository.findById(periodeId)
            .getOrElse { return ResponseEntity.notFound().build() }
        return ResponseEntity.ok().body(rekeningCashflowService.getCashflowVoorHulpvrager(hulpvrager, periode))
    }

    @PostMapping("/hulpvrager/{hulpvragerId}")
    fun creeerNieuweRekeningVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @RequestBody rekeningGroepLijst: List<RekeningGroep.RekeningGroepDTO>,
    ): ResponseEntity<Any>  {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST RekeningController.creeerNieuweRekeningVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(rekeningService.saveAll(hulpvrager, rekeningGroepLijst))
    }
}

