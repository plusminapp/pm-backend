package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.domain.Periode.PeriodeDTO
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.PeriodeService
import io.vliet.plusmin.service.PeriodeSluitenService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/periode")
class PeriodeController {

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeSluitenService: PeriodeSluitenService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerController: GebruikerController

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)


    @Operation(summary = "GET de periodes voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}")
    fun getPeriodesVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET PeriodeController.getPeriodesVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return ResponseEntity.ok().body(periodeRepository.getPeriodesVoorGebruiker(hulpvrager).map {
            PeriodeDTO(it.id, formatter.format(it.periodeStartDatum),formatter.format(it.periodeEindDatum),  it.periodeStatus)})
    }

    @Operation(summary = "PUT sluit een periode voor hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}/sluiten/{periodeId}")
    fun sluitPeriodeVoorHulpvrager(
        @Valid @RequestBody saldoLijst: List<Saldo.SaldoDTO>,
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET PeriodeController.sluitPeriodesVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        return ResponseEntity.ok().body(periodeSluitenService.sluitPeriode(hulpvrager, periodeId, saldoLijst))
    }

    @Operation(summary = "POST ruim een periode op (verwijder de betalingen) voor hulpvrager")
    @PostMapping("/hulpvrager/{hulpvragerId}/opruimen/{periodeId}")
    fun ruimPeriodeOpVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET PeriodeController.ruimPeriodeOpVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        val periode = periodeRepository.getPeriodeById(periodeId)
        if (periode == null) {
            return ResponseEntity.badRequest().body("Periode met id $periodeId bestaat niet.")
        }
        try {
            periodeSluitenService.ruimPeriodeOp(hulpvrager, periode)
        } catch (e: Exception) {
            logger.error("Fout bij opruimen van periode $periodeId voor hulpvrager ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body("Fout bij opruimen van periode: ${e.message}")
        }
        return ResponseEntity.ok().body("Periode $periodeId voor hulpvrager ${hulpvrager.email} is succesvol opgeruimd.")
    }

    @Operation(summary = "GET voorstel voor het sluiten van een periode voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}/sluitvoorstel/{periodeId}")
    fun sluitPeriodeVoorstelVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET PeriodeController.sluitPeriodeVoorstelVoorHulpvrager() $periodeId voor ${hulpvrager.email} door ${vrijwilliger.email}.")
        return ResponseEntity.ok().body(periodeSluitenService.voorstelPeriodeSluiten (hulpvrager, periodeId))
    }
}

