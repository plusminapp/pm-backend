package io.vliet.plusmin.controller

//import io.vliet.plusmin.service.Camt053Service
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.repository.BetalingDao
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.service.BetalingService
import io.vliet.plusmin.service.BetalingvalidatieService
import io.vliet.plusmin.service.GebruikerService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/betalingen")
class BetalingController {

    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var betalingService: BetalingService

    @Autowired
    lateinit var betalingDao: BetalingDao

    @Autowired
    lateinit var betalingvalidatieService: BetalingvalidatieService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(
        summary = "Get betalingen hulpvrager",
        description = "GET alle betalingen van een hulpvrager (alleen voor VRIJWILLIGERs)"
    )
    @GetMapping("/administratie/{administratieId}")
    fun getAlleBetalingenVanHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
        @RequestParam("size", defaultValue = "25", required = false) sizeAsString: String,
        @RequestParam("page", defaultValue = "0", required = false) pageAsString: String,
        @RequestParam("sort", defaultValue = "boekingsdatum:asc", required = false) sort: String,
        @RequestParam("query", defaultValue = "", required = false) query: String,
        @RequestParam("status", required = false) status: String?,
        @Parameter(description = "Formaat: yyyy-mm-dd")
        @RequestParam("fromDate", defaultValue = "", required = false) fromDate: String,
        @Parameter(description = "Formaat: yyyy-mm-dd")
        @RequestParam("toDate", defaultValue = "", required = false) toDate: String,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("GET BetalingController.getAlleBetalingenVanHulpvrager voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        val betalingen =
            betalingDao
                .search(administratie, sizeAsString, pageAsString, sort, query, status, fromDate, toDate)
        return ResponseEntity.ok().body(betalingen)
    }

    @Operation(summary = "DELETE betaling")
    @DeleteMapping("/{betalingId}")
    fun deleteBetaling(
        @PathVariable("betalingId") betalingId: Long,
    ): ResponseEntity<Any> {
        val betaling = betalingRepository.findById2(betalingId) ?: run {
            logger.warn("Betaling met id $betalingId niet gevonden.")
            return ResponseEntity("Betaling met id $betalingId niet gevonden.", HttpStatus.NOT_FOUND)
        }
        val (administratie, gebruiker) = gebruikerService.checkAccess(betaling.administratie.id)
        logger.info("DELETE BetalingController.deleteBetaling met id $betalingId voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        return ResponseEntity.ok().body(betalingRepository.delete(betaling))
    }

    @PostMapping("/administratie/{administratieId}/list")
    fun creeerNieuweBetalingenVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
        @Valid @RequestBody betalingList: List<BetalingDTO>,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("POST BetalingController.creeerNieuweBetalingVoorHulpvrager voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        val betalingen = betalingService.creeerBetalingLijst(administratie, betalingList)
        return ResponseEntity.ok().body(betalingen)
    }

  @PostMapping("/administratie/{administratieId}")
    fun creeerNieuweBetalingVoorHulpvrager(
      @PathVariable("administratieId") administratieId: Long,
      @Valid @RequestBody betalingDTO: BetalingDTO,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("POST BetalingController.creeerNieuweBetalingVoorHulpvrager voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        val betaling = betalingService.creeerBetaling(administratie, betalingDTO)
        return ResponseEntity.ok().body(betaling)
    }

    @PutMapping("/{betalingId}")
    fun wijzigBetaling(
        @PathVariable("betalingId") betalingId: Long,
        @Valid @RequestBody betalingDTO: BetalingDTO,
    ): ResponseEntity<Any> {
        val betalingOpt = betalingRepository.findById(betalingId)
        if (betalingOpt.isEmpty) {
            logger.warn("Betaling met id $betalingId niet gevonden.")
            return ResponseEntity("Betaling met id $betalingId niet gevonden.", HttpStatus.NOT_FOUND)
        }
        val betaling = betalingOpt.get()
        val (administratie, gebruiker) = gebruikerService.checkAccess(betaling.administratie.id)
        logger.info("PUT BetalingController.wijzigBetaling met id $betalingId voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        return ResponseEntity.ok().body(betalingService.update(betaling, betalingDTO).toDTO())
    }

    @GetMapping("/administratie/{administratieId}/betalingvalidatie")
    fun getDatumLaatsteBetaling(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<LocalDate?> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("GET BetalingController.getDatumLaatsteBetaling voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        return ResponseEntity.ok().body(betalingRepository.findDatumLaatsteBetalingBijAdministratie(administratie))
    }

    @PutMapping("/administratie/{administratieId}/betalingvalidatie")
    fun valideerOcrBetalingen(
        @PathVariable("administratieId") administratieId: Long,
        @Valid @RequestBody betalingValidatieWrapper: Betaling.BetalingValidatieWrapper,
    ): ResponseEntity<Betaling.BetalingValidatieWrapper> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT BetalingController.valideerOcrBetalingen voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        return ResponseEntity.ok().body(betalingvalidatieService.valideerBetalingen(administratie, betalingValidatieWrapper))
    }

    @Operation(summary = "GET valideer betalingen voor hulpvrager")
    @GetMapping("/administratie/{administratieId}/valideer-betalingen")
    fun valideerBetalingenVoorHulpvrager(
        @PathVariable("administratieId") administratieId: Long,
    ): List<Betaling> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("GET BetalingController.valideerBetalingenVoorHulpvrager() voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        return betalingService.valideerBetalingenVoorGebruiker(administratie)
    }
}
