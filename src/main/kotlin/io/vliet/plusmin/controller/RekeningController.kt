package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.Rekening.RekeningDTO
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.service.RekeningService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.jvm.optionals.getOrElse

@RestController
@RequestMapping("/rekening")
class RekeningController {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var gebruikerController: GebruikerController

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    // Iedereen mag de eigen rekening opvragen
    @Operation(summary = "GET de eigen rekeningen op basis van de JWT")
    @GetMapping("")
    fun findRekeningen(): List<Rekening> {
        val gebruiker = gebruikerController.getJwtGebruiker()
        logger.info("GET RekeningController.findRekeningen() voor gebruiker ${gebruiker.email}.")
        return rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
    }

    @Operation(summary = "GET de rekening op basis van de JWT van een rekening")
    @GetMapping("/hulpvrager/{hulpvragerId}/periode/{periodeId}")
    fun getAlleRekeningenVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET BetalingController.getAlleRekeningenVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val periode = periodeRepository.findById(periodeId)
            .getOrElse { return ResponseEntity.notFound().build() }
        return ResponseEntity.ok().body(rekeningService.findRekeningenVoorGebruikerEnPeriode(hulpvrager, periode))
    }

    @PostMapping("/hulpvrager/{hulpvragerId}")
    fun creeerNieuweRekeningVoorHulpvrager(
        @RequestBody rekeningList: List<RekeningDTO>,
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any>  {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST BetalingController.creeerNieuweRekeningVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(rekeningService.saveAll(hulpvrager, rekeningList))
    }
}

