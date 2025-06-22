package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.repository.AflossingRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.SaldoRepository
import io.vliet.plusmin.service.AflossingGrafiekService
import io.vliet.plusmin.service.AflossingService
import io.vliet.plusmin.service.PeriodeService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/aflossing")
class AflossingController {
    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

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
    lateinit var periodeService: PeriodeService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de saldi aflossingen van een hulpvrager op datum")
    @GetMapping("/hulpvrager/{hulpvragerId}/datum/{datum}")
    fun getAflossingenSaldiVoorHulpvragerOpDatum(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("datum") datum: String,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET AflossingController.getAflossingenSaldiVoorHulpvragerOpDatum voor ${hulpvrager.email} op ${datum} door ${vrijwilliger.email}")
//        val peilDatum = LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE)
//        val openingPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(hulpvrager)
//        val periode = periodeService.getPeriode(hulpvrager, peilDatum)
//        val rekeningGroepen = rekeningGroepRepository.findRekeningGroepenOpSoort(hulpvrager, RekeningGroep.RekeningGroepSoort.AFLOSSING)
        val rekeningen = aflossingService.getAflossingenOpDatum(
            hulpvrager,
            LocalDate.parse(datum, DateTimeFormatter.ISO_LOCAL_DATE)
        )
        return ResponseEntity.ok().body(rekeningen)
    }
}
