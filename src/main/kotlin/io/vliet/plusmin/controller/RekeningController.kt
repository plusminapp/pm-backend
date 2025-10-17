package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.service.CashflowService
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.RekeningService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.jvm.optionals.getOrElse

@RestController
@RequestMapping("/rekeningen")
class RekeningController {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var cashflowService: CashflowService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de geldige rekeningen in een periode")
    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun getAlleRekeningenPerBetalingsSoortVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("GET RekeningController.getAlleRekeningenPerBetalingsSoortVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val periode = periodeRepository.findById(periodeId)
            .getOrElse { return ResponseEntity.notFound().build() }
        return ResponseEntity.ok().body(rekeningService.rekeningGroepenPerBetalingsSoort(hulpvrager, periode))
    }

    @Operation(summary = "GET de cashflow in een periode")
    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}/cashflow")
    fun getCashflowVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("GET RekeningController.getCashflowVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val periode = periodeRepository.findById(periodeId)
            .getOrElse { return ResponseEntity.notFound().build() }
        return ResponseEntity.ok().body(cashflowService.getCashflow(hulpvrager, periode, true))
    }

    @PostMapping("/hulpvrager/{hulpvragerId}")
    fun creeerNieuweRekeningVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @RequestBody rekeningGroepLijst: List<RekeningGroep.RekeningGroepDTO>,
    ): ResponseEntity<Any>  {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("POST RekeningController.creeerNieuweRekeningVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(rekeningService.saveAll(hulpvrager, rekeningGroepLijst))
    }
}

