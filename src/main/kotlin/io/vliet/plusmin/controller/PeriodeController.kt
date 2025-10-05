package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.ErrorResponse
import io.vliet.plusmin.domain.Periode.PeriodeDTO
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.PeriodeUpdateService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/periode")
class PeriodeController {

    @Autowired
    lateinit var periodeUpdateService: PeriodeUpdateService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)


    @Operation(summary = "GET de periodes voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}")
    fun getPeriodesVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("GET PeriodeController.getPeriodesVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return ResponseEntity.ok().body(periodeRepository.getPeriodesVoorGebruiker(hulpvrager).map {
            PeriodeDTO(
                it.id,
                formatter.format(it.periodeStartDatum),
                formatter.format(it.periodeEindDatum),
                it.periodeStatus
            )
        })
    }

    @Operation(summary = "PUT sluit een periode voor hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}/sluiten/{periodeId}")
    fun sluitPeriodeVoorHulpvrager(
        @Valid @RequestBody saldoLijst: List<Saldo.SaldoDTO>,
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("PUT PeriodeController.sluitPeriodeVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        try {
            periodeUpdateService.sluitPeriode(hulpvrager, periodeId, saldoLijst)
        } catch (e: Exception) {
            logger.error("Fout bij sluiten van periode $periodeId voor hulpvrager ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body("Fout bij sluiten van periode: ${e.message}")
        }
        return ResponseEntity.ok().body("Periode $periodeId voor hulpvrager ${hulpvrager.email} is succesvol gesloten.")
    }

    @Operation(summary = "PUT ruim een periode op (verwijder de betalingen) voor hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}/opruimen/{periodeId}")
    fun ruimPeriodesOpVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("PUT PeriodeController.ruimPeriodesOpVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        val periode = periodeRepository.getPeriodeById(periodeId)
        if (periode == null) {
            return ResponseEntity.status(404).body("Periode met id $periodeId bestaat niet.")
        }
        try {
            periodeUpdateService.ruimPeriodeOp(hulpvrager, periode)
        } catch (e: Exception) {
            logger.error("Fout bij opruimen van periode $periodeId voor hulpvrager ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body("Fout bij opruimen van periode: ${e.message}")
        }
        return ResponseEntity.ok()
            .body("Periode $periodeId voor hulpvrager ${hulpvrager.email} is succesvol opgeruimd.")
    }

    @Operation(summary = "PUT heropen een periode (en verwijder de saldi) voor hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}/heropenen/{periodeId}")
    fun heropenPeriodeVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("PUT PeriodeController.heropenPeriodeVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        val periode = periodeRepository.getPeriodeById(periodeId)
        if (periode == null) {
            return ResponseEntity.status(404).body("Periode met id $periodeId bestaat niet.")
        }
        try {
            periodeUpdateService.heropenPeriode(hulpvrager, periode)
        } catch (e: Exception) {
            logger.error("Fout bij heropenen van periode $periodeId voor hulpvrager ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body("Fout bij heropenen van periode: ${e.message}")
        }
        return ResponseEntity.ok()
            .body("Periode $periodeId voor hulpvrager ${hulpvrager.email} is succesvol heropenend.")
    }

    @Operation(summary = "PUT wijzig een periode voor hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}/wijzig-periode-opening/{periodeId}")
    fun wijzigPeriodeOpeningVoorHulpvrager(
        @Valid @RequestBody nieuweOpeningsSaldi: List<Saldo.SaldoDTO>,
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("PUT PeriodeController.wijzigPeriodeOpeningVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        val opgeslagenOpeningsSaldi = try {
            periodeUpdateService.wijzigPeriodeOpening(hulpvrager, periodeId, nieuweOpeningsSaldi)
        } catch (e: Exception) {
            logger.error("Fout bij wijzigen van periode $periodeId voor hulpvrager ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body(
                ErrorResponse(
                    "wijzigPeriodeOpeningVoorHulpvragerError",
                    "Fout bij wijzigen van periodeopening: ${e.message}"
                )
            )
        }
        return ResponseEntity.ok().body(opgeslagenOpeningsSaldi)
    }

    @Operation(summary = "GET voorstel voor het sluiten van een periode voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}/sluitvoorstel/{periodeId}")
    fun sluitPeriodeVoorstelVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(hulpvragerId)
        logger.info("GET PeriodeController.sluitPeriodeVoorstelVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        return ResponseEntity.ok().body(periodeUpdateService.voorstelPeriodeSluiten(hulpvrager, periodeId))
    }
}

