package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Aflossing
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.domain.Saldo.SaldoDTO
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.service.PeriodeService
import io.vliet.plusmin.service.SaldoService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/saldo")
class SaldoController {

    @Autowired
    lateinit var saldoService: SaldoService

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var gebruikerController: GebruikerController

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de stand voor hulpvrager op datum")
    @GetMapping("/hulpvrager/{hulpvragerId}/stand/{datum}")
    fun getStandOpDatumVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("datum") datum: String,
    ): StandDTO {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET SaldoController.getStandOpDatumVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val peilDatum = LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE)

        val openingPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(hulpvrager)
        val periode = periodeService.getPeriode(hulpvrager, peilDatum)
        return saldoService.getStandOpDatum(openingPeriode, peilDatum, periode)
    }

//    @Operation(summary = "PUT de saldi voor hulpvrager")
//    @PutMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
//    fun upsertSaldoVoorHulpvrager(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @PathVariable("periodeId") periodeId: Long,
//        @Valid @RequestBody saldiDTO: List<SaldoDTO>): ResponseEntity<Any> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("PUT SaldoController.upsertSaldoVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email} met datum ${saldiDTO.saldoStartDatum}")
//        return ResponseEntity.ok().body(saldoService.upsert(hulpvrager, periodeId, saldiDTO))
//    }
//

    data class StandDTO(
        val periodeStartDatum: LocalDate,
        val peilDatum: LocalDate,
        val datumLaatsteBetaling: LocalDate?,
        val openingsBalans: List<SaldoDTO>,
        val mutatiesOpDatum: List<SaldoDTO>,
        val balansOpDatum: List<SaldoDTO>,
        val resultaatOpDatum: List<SaldoDTO>,
        val resultaatSamenvattingOpDatumDTO: Saldo.ResultaatSamenvattingOpDatumDTO,
        val geaggregeerdResultaatOpDatum: List<SaldoDTO>,
//        val aflossingenOpDatum: List<Aflossing.AflossingDTO>,
//        val geaggregeerdeAflossingenOpDatum: Aflossing.AflossingDTO? = null
    )
}

