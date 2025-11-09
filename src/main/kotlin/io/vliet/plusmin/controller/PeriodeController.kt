package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Periode.PeriodeDTO
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.PeriodeService
import io.vliet.plusmin.service.PeriodeUpdateService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/periodes")
class PeriodeController {

    @Autowired
    lateinit var periodeUpdateService: PeriodeUpdateService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var gebruikerService: GebruikerService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)


    @Operation(summary = "GET de periodes voor hulpvrager")
    @GetMapping("/administratie/{administratieId}")
    fun getPeriodesVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("GET PeriodeController.getPeriodesVoorHulpvrager() voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}.")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return ResponseEntity.ok().body(periodeRepository.getPeriodesVoorAdministrtatie(hulpvrager).map {
            PeriodeDTO(
                it.id,
                formatter.format(it.periodeStartDatum),
                formatter.format(it.periodeEindDatum),
                it.periodeStatus
            )
        })
    }

    @Operation(summary = "PUT sluit een periode voor hulpvrager")
    @PutMapping("/administratie/{administratieId}/sluiten/{periodeId}")
    fun sluitPeriodeVoorHulpvrager(
        @Valid @RequestBody saldoLijst: List<Saldo.SaldoDTO>,
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT PeriodeController.sluitPeriodeVoorHulpvrager() $periodeId voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}.")
        periodeUpdateService.sluitPeriode(hulpvrager, periodeId, saldoLijst)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "PUT ruim een periode op (verwijder de betalingen) voor hulpvrager")
    @PutMapping("/administratie/{administratieId}/opruimen/{periodeId}")
    fun ruimPeriodesOpVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT PeriodeController.ruimPeriodesOpVoorHulpvrager() $periodeId voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}.")
        val periode = periodeService.getPeriode(periodeId, hulpvrager)
        periodeUpdateService.ruimPeriodeOp(hulpvrager, periode)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "PUT heropen een periode (en verwijder de saldi) voor hulpvrager")
    @PutMapping("/administratie/{administratieId}/heropenen/{periodeId}")
    fun heropenPeriodeVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT PeriodeController.heropenPeriodeVoorHulpvrager() $periodeId voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}.")
        val periode = periodeService.getPeriode(periodeId, hulpvrager)
        periodeUpdateService.heropenPeriode(hulpvrager, periode)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "PUT wijzig een periode voor hulpvrager")
    @PutMapping("/administratie/{administratieId}/wijzig-periode-opening/{periodeId}")
    fun wijzigPeriodeOpeningVoorHulpvrager(
        @Valid @RequestBody nieuweOpeningsSaldi: List<Saldo.SaldoDTO>,
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT PeriodeController.wijzigPeriodeOpeningVoorHulpvrager() $periodeId voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}.")
        periodeUpdateService.wijzigPeriodeOpening(hulpvrager, periodeId, nieuweOpeningsSaldi)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "GET voorstel voor het sluiten van een periode voor hulpvrager")
    @GetMapping("/administratie/{administratieId}/sluitvoorstel/{periodeId}")
    fun sluitPeriodeVoorstelVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("GET PeriodeController.sluitPeriodeVoorstelVoorHulpvrager() $periodeId voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}.")
        return ResponseEntity.ok().body(periodeUpdateService.voorstelPeriodeSluiten(hulpvrager, periodeId))
    }
}

