package io.vliet.plusmin.controller

//import io.vliet.plusmin.service.Camt053Service
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.domain.Budget
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.repository.BetalingDao
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.service.BetalingvalidatieService
import io.vliet.plusmin.service.BetalingService
import io.vliet.plusmin.service.Camt053Service
import io.vliet.plusmin.service.PagingService
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import org.apache.commons.io.input.BOMInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate

@RestController
@RequestMapping("/betalingen")
class BetalingController {

    @Autowired
    lateinit var gebruikerController: GebruikerController

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var betalingService: BetalingService

    @Autowired
    lateinit var betalingDao: BetalingDao

    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Autowired
    lateinit var camt053Service: Camt053Service

    @Autowired
    lateinit var betalingvalidatieService: BetalingvalidatieService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<String> {
        val stackTraceElement = ex.stackTrace.firstOrNull { it.className.startsWith("io.vliet") }
            ?: ex.stackTrace.firstOrNull()
        val locationInfo = stackTraceElement?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
        val errorMessage = "${ex.message}$locationInfo"
        logger.error(errorMessage)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage)
    }

    @Operation(summary = "GET alle eigen betalingen (voor alle gebruikers)")
    @GetMapping("")
    fun getAlleBetalingen(
        @RequestParam("size", defaultValue = "25", required = false) sizeAsString: String,
        @RequestParam("page", defaultValue = "0", required = false) pageAsString: String,
        @RequestParam("sort", defaultValue = "boekingsdatum:asc", required = false) sort: String,
        @RequestParam("query", defaultValue = "", required = false) query: String,
        @RequestParam("status", required = false) status: String?,
        @RequestParam("fromDate", defaultValue = "", required = false) fromDate: String,
        @RequestParam("toDate", defaultValue = "", required = false) toDate: String,
    ): ResponseEntity<PagingService.ContentWrapper> {
        val gebruiker = gebruikerController.getJwtGebruiker()
        logger.info("GET BetalingController.getAlleBetalingen voor ${gebruiker.email}")
        val betalingen =
            betalingDao.search(gebruiker, sizeAsString, pageAsString, sort, query, status, fromDate, toDate)
        return ResponseEntity.ok().body(betalingen)
    }

    @Operation(
        summary = "Get betalingen hulpvrager",
        description = "GET alle betalingen van een hulpvrager (alleen voor VRIJWILLIGERs)"
    )
    @GetMapping("/hulpvrager/{hulpvragerId}")
    fun getAlleBetalingenVanHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
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
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET BetalingController.getAlleBetalingenVanHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val betalingen =
            betalingDao
                .search(hulpvrager, sizeAsString, pageAsString, sort, query, status, fromDate, toDate)
        return ResponseEntity.ok().body(betalingen)
    }

    @Operation(summary = "DELETE alle betalingen (alleen voor ADMINs)")
    @RolesAllowed("ADMIN")
    @DeleteMapping("")
    fun deleteAlleBetalingen(): ResponseEntity<Any> {
        return ResponseEntity.ok().body(betalingRepository.deleteAll())
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
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(betaling.gebruiker.id)
        logger.info("DELETE BetalingController.deleteBetaling met id $betalingId voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(betalingRepository.delete(betaling))
    }

    @PostMapping("/hulpvrager/{hulpvragerId}/list")
    fun creeerNieuweBetalingenVoorHulpvrager(
        @Valid @RequestBody betalingList: List<BetalingDTO>,
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST BetalingController.creeerNieuweBetalingVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val betalingen = betalingService.creeerBetalingLijst(hulpvrager, betalingList)
        return ResponseEntity.ok().body(betalingen)
    }

  @PostMapping("/hulpvrager/{hulpvragerId}")
    fun creeerNieuweBetalingVoorHulpvrager(
      @Valid @RequestBody betalingDTO: BetalingDTO,
      @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("POST BetalingController.creeerNieuweBetalingVoorHulpvrager voor ${hulpvrager.email} door ${vrijwilliger.email}")
        val betaling = betalingService.creeerBetaling(hulpvrager, betalingDTO)
        return ResponseEntity.ok().body(betaling)
    }

    @PutMapping("/{betalingId}")
    fun wijzigBetaling(
        @Valid @RequestBody betalingDTO: BetalingDTO,
        @PathVariable("betalingId") betalingId: Long,
    ): ResponseEntity<Any> {
        val betalingOpt = betalingRepository.findById(betalingId)
        if (betalingOpt.isEmpty) {
            logger.warn("Betaling met id $betalingId niet gevonden.")
            return ResponseEntity("Betaling met id $betalingId niet gevonden.", HttpStatus.NOT_FOUND)
        }
        val betaling = betalingOpt.get()
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(betaling.gebruiker.id)
        logger.info("PUT BetalingController.wijzigBetaling met id $betalingId voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(betalingService.update(betaling, betalingDTO).toDTO())
    }

    @GetMapping("/hulpvrager/{hulpvragerId}/betalingvalidatie")
    fun getDatumLaatsteBetaling(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<LocalDate?> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT BetalingController.getDatumLaatsteBetaling voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(betalingRepository.findLaatsteBetalingDatumBijGebruiker(hulpvrager))
    }

    @PutMapping("/hulpvrager/{hulpvragerId}/betalingvalidatie")
    fun valideerOcrBetalingen(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @Valid @RequestBody betalingValidatieWrapper: Betaling.BetalingValidatieWrapper,
    ): ResponseEntity<Betaling.BetalingValidatieWrapper> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT BetalingController.valideerOcrBetalingen voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(betalingvalidatieService.valideerBetalingen(hulpvrager, betalingValidatieWrapper))
    }

//    @Operation(summary = "GET valideer budgetten voor hulpvrager")
//    @GetMapping("/hulpvrager/{hulpvragerId}/valideer-budgetten")
//    fun valideerBudgettenVoorHulpvrager(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//    ): List<Betaling> {
//        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
//        logger.info("GET BudgetController.valideerBudgettenVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email}")
//        return betalingService.valideerBudgettenVoorGebruiker(hulpvrager)
//    }

//    @Operation(summary = "POST CAMT053 betalingen (voor HULPVRAGERS en VRIJWILLIGERs)")
//    @PostMapping("/camt053/{hulpvragerId}", consumes = ["multipart/form-data"])
//    fun verwerkCamt053(
//        @PathVariable("hulpvragerId") hulpvragerId: Long,
//        @RequestParam("file") file: MultipartFile,
//        @RequestParam("debug") debug: Boolean
//    ): ResponseEntity<Any> {
//        if (file.size > 4000000) {
//            logger.warn("BetalingController.verwerkCamt053 bestand te groot voor gebruiker $hulpvragerId")
//            return ResponseEntity(HttpStatus.PAYLOAD_TOO_LARGE)
//        }
//        val hulpvrager = gebruikerRepository.selectById(hulpvragerId) ?: run {
//            logger.error("BetalingController.verwerkCamt053: gebruiker $hulpvragerId bestaat niet")
//            return ResponseEntity(
//                "BetalingController.verwerkCamt053: gebruiker $hulpvragerId bestaat niet",
//                HttpStatus.NOT_FOUND
//            )
//        }
//
//        val jwtGebruiker = gebruikerController.getJwtGebruiker()
//        if (jwtGebruiker.id != hulpvrager.id && jwtGebruiker.id != hulpvrager.vrijwilliger?.id && !jwtGebruiker.roles.contains(Gebruiker.Role.ROLE_ADMIN)) {
//            logger.error("BetalingController.verwerkCamt053: gebruiker ${jwtGebruiker.email} wil een camt053 bestand uploaden voor ${hulpvrager.email}")
//            return ResponseEntity(
//                "BetalingController.verwerkCamt053: gebruiker ${jwtGebruiker.email} wil een camt053 bestand uploaden voor ${hulpvrager.email}",
//                HttpStatus.FORBIDDEN
//            )
//        }
//        logger.info("BetalingController.verwerkCamt053: gebruiker ${jwtGebruiker.email} upload camt053 bestand voor ${hulpvrager.email}")
//        val result = camt053Service.loadCamt053File(
//            hulpvrager,
//            BufferedReader(
//                InputStreamReader(
//                    BOMInputStream.builder()
//                        .setInputStream(file.inputStream)
//                        .get()
//                )
//            ),
//            debug
//        )
//        return ResponseEntity.ok().body(result)
//    }
}
