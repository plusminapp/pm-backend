package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.domain.Saldo.SaldoDTO
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.StandService
import io.vliet.plusmin.service.StandStartVanPeriodeService
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
    lateinit var standStartVanPeriodeService: StandStartVanPeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de stand voor hulpvrager op datum")
    @GetMapping("/administratie/{administratieId}/datum/{datum}")
    fun getStandOpDatumVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("datum") datum: String,
    ): StandDTO {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("GET SaldoController.getStandOpDatumVoorHulpvrager() voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject} op datum $datum")
        val peilDatum = LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE)
        return standService.getStandOpDatum(hulpvrager, peilDatum)
    }

    @Operation(summary = "GET de stand voor hulpvrager op datum")
    @GetMapping("/administratie/{administratieId}/periode/{periodeId}/openingsbalans")
    fun getOpeningsBalansVoorPeriode(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): List<SaldoDTO> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("GET SaldoController.getOpeningsBalansVoorPeriode() voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject} voor periode datum $periodeId")
        return standStartVanPeriodeService
            .berekenStartSaldiVanPeriode(hulpvrager, periodeId)
    }
    @Operation(summary = "GET de spaarsaldi controle voor hulpvrager")
    @GetMapping("/administratie/{administratieId}/checkspaarsaldi")
    fun checkSaldi(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("GET SaldoController.checkSaldi() voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}")
        return ResponseEntity.ok().build()
    }

    data class StandDTO(
        val periodeStartDatum: LocalDate,
        val peilDatum: LocalDate,
        val datumLaatsteBetaling: LocalDate?,
        val budgetHorizon: LocalDate,
        val reserveringsHorizon: LocalDate,
        val resultaatOpDatum: List<SaldoDTO>,
        val resultaatSamenvattingOpDatum: Saldo.ResultaatSamenvattingOpDatumDTO,
        val geaggregeerdResultaatOpDatum: List<SaldoDTO>,
    )
}

