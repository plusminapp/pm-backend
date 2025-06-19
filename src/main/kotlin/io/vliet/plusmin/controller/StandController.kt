package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.domain.Saldo.SaldoDTO
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.PeriodeService
import io.vliet.plusmin.service.StandService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/stand")
class StandController {

    @Autowired
    lateinit var standService: StandService

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerController: GebruikerController

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de stand voor hulpvrager op datum")
    @GetMapping("/hulpvrager/{hulpvragerId}/datum/{datum}")
    fun getStandOpDatumVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("datum") datum: String,
    ): StandDTO {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET SaldoController.getStandOpDatumVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email} op datum $datum")
        val peilDatum = LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE)
        return standService.getStandOpDatum(hulpvrager, peilDatum)
    }

    @Operation(summary = "GET de stand voor hulpvrager op periode")
    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun getStandOpPeriodeVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET SaldoController.getStandOpPeriodeVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email} op periode $periodeId")
        val periode = periodeRepository.getPeriodeById(periodeId)
        if (periode == null) {
            return ResponseEntity.badRequest().body("Periode met id $periodeId bestaat niet.")
        }
        return ResponseEntity.ok(standService.getStandOpDatum(hulpvrager, periode.periodeEindDatum))
    }

    data class StandDTO(
        val periodeStartDatum: LocalDate,
        val peilDatum: LocalDate,
        val datumLaatsteBetaling: LocalDate?,
        val resultaatOpDatum: List<SaldoDTO>,
        val resultaatSamenvattingOpDatum: Saldo.ResultaatSamenvattingOpDatumDTO,
        val geaggregeerdResultaatOpDatum: List<SaldoDTO>,
    )
}

