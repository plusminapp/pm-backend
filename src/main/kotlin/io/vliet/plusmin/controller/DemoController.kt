package io.vliet.plusmin.controller

import io.vliet.plusmin.repository.DemoRepository
import io.vliet.plusmin.service.DemoService
import io.vliet.plusmin.service.GebruikerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/demo")
class DemoController {

    @Autowired
    lateinit var demoService: DemoService

    @Autowired
    lateinit var demoRepository: DemoRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @PutMapping("/administratie/{administratieId}/configureer")
    fun configureerPeriode(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT DemoController.configureerPeriode voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        demoService.configureerDemoBetalingen(administratie)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/administratie/{administratieId}/verwijderVanPeriode/{periodeId}")
    fun deleteBetalingenInPeriode(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<String> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("DELETE DemoController.deleteBetalingenInPeriode voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        demoService.deleteBetalingenInPeriode(administratie, periodeId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/administratie/{administratieId}/reset")
    fun resetGebruikerData(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<String> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT DemoController.resetGebruikerData voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        demoRepository.resetGebruikerData(administratie.id)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/administratie/{administratieId}/vandaag")
    fun getVandaag(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<String?> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("GET DemoController.getVandaag voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        return ResponseEntity.ok(administratie.vandaag?.toString())
    }

    @PutMapping("/administratie/{administratieId}/vandaag/{vandaag}")
    fun putVandaag(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("vandaag") vandaag: String? = null,
    ): ResponseEntity<Int> {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT DemoController.putVandaag voor ${administratie.naam} door ${gebruiker.bijnaam}/${gebruiker.subject}")
        demoService.putVandaag(administratie, vandaag)
        return ResponseEntity.ok().build()
    }
}
