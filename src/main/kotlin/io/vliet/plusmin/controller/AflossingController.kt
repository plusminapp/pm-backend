package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Aflossing.AflossingDTO
import io.vliet.plusmin.repository.AflossingRepository
import io.vliet.plusmin.repository.SaldoRepository
import io.vliet.plusmin.service.AflossingGrafiekService
import io.vliet.plusmin.service.AflossingService
import io.vliet.plusmin.service.PeriodeService
import io.vliet.plusmin.service.SaldoService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/aflossing")
class AflossingController {
    @Autowired
    lateinit var aflossingRepository: AflossingRepository

    @Autowired
    lateinit var aflossingService: AflossingService

    @Autowired
    lateinit var aflossingGrafiekService: AflossingGrafiekService

    @Autowired
    lateinit var gebruikerController: GebruikerController

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var saldoService: SaldoService

    @Autowired
    lateinit var periodeService: PeriodeService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de aflossingen van een hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}")
    fun getAflossingenVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(aflossingRepository.findAflossingenVoorGebruiker(hulpvrager))
    }

    @Operation(summary = "GET het totale aflossingsbedrag van een hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}/aflossingsbedrag")
    fun getAflossingsbedragVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val aflossingsBedrag = aflossingRepository.findAflossingenVoorGebruiker(hulpvrager)
            .fold(BigDecimal(0)) { acc, aflossing -> acc + aflossing.aflossingsBedrag }
        return ResponseEntity.ok().body(aflossingsBedrag)
    }

    @Operation(summary = "GET de saldi aflossingen van een hulpvrager op datum")
    @GetMapping("/hulpvrager/{hulpvragerId}/datum/{datum}")
    fun getAflossingenSaldiVoorHulpvragerOpDatum(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("datum") datum: String,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenSaldiVoorHulpvragerOpDatum voor ${hulpvrager.email} op ${datum} door ${vrijwilliger.email}")
        val peilDatum = LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE)
        val openingPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(hulpvrager)
        val periodeStartDatum = maxOf(
            openingPeriode.periodeEindDatum.plusDays(1),
            periodeService.berekenPeriodeDatums(hulpvrager.periodeDag, peilDatum).first
        )
        val standDTO = saldoService.getStandOpDatum(
            openingPeriode,
            periodeStartDatum,
            peilDatum
        )
        return ResponseEntity.ok().body(standDTO.aflossingenOpDatum)
    }

    @Operation(summary = "PUT (upsert) (nieuwe) aflossingen van een hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}")
    fun creeerNieuweaflossingVoorHulpvrager(
        @Valid @RequestBody aflossingList: List<AflossingDTO>,
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT AflossingController.creeerNieuweaflossingVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(aflossingService.creeerAflossingen(hulpvrager, aflossingList))
    }

    @Operation(summary = "GET de Aflossing GrafiekSeries voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}/series")
    fun getAflossingenGrafiekSeriesVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenSeriesVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(aflossingGrafiekService.genereerAflossingGrafiekSeries(hulpvrager))
    }

    @Operation(summary = "GET de Aflossing GrafiekData voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}/data")
    fun getAflossingenGrafiekDataVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenDataVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(aflossingGrafiekService.genereerAflossingGrafiekData(hulpvrager))
    }

    @Operation(summary = "GET de Aflossing GrafiekDataen GrafiekSerie voor hulpvrager")
    @GetMapping("/hulpvrager/{hulpvragerId}/aflossinggrafiek")
    fun getAflossingenGrafiekDTOVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenDataVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(
            AflossingGrafiekService.AflossingGrafiekDTO(
                aflossingGrafiekSerie = aflossingGrafiekService.genereerAflossingGrafiekSeries(hulpvrager),
                aflossingGrafiekData = aflossingGrafiekService.genereerAflossingGrafiekData(hulpvrager),
            )
        )
    }

    @Operation(summary = "DELETE Aflossing op id")
    @DeleteMapping("/{aflossingId}")
    @Transactional
    fun verwijderAflossing(
        @PathVariable("aflossingId") aflossingId: Long
    ): ResponseEntity<Any> {
        val aflossingOpt = aflossingRepository.findById(aflossingId)
        if (aflossingOpt.isEmpty) return ResponseEntity(
            "Aflossing met id $aflossingId niet gevonden.",
            HttpStatus.NOT_FOUND
        )
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(aflossingOpt.get().rekening.gebruiker.id)
        logger.info("DELETE AflossingController.verwijderAflossing voor ${hulpvrager.email} door ${vrijwilliger.email}")
        saldoRepository.deleteByRekening(aflossingOpt.get().rekening)
        aflossingRepository.deleteById(aflossingId)
        return ResponseEntity.ok().body("Aflossing met id $aflossingId verwijderd")
    }
}

