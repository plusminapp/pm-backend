package io.vliet.plusmin.controller

import io.vliet.plusmin.repository.DemoRepository
import io.vliet.plusmin.service.DemoService
import io.vliet.plusmin.service.GebruikerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT DemoController.configureerPeriode voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}")
        demoService.configureerDemoBetalingen(hulpvrager)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/administratie/{administratieId}/verwijderVanPeriode/{periodeId}")
    fun deleteBetalingenInPeriode(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<String> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("DELETE DemoController.deleteBetalingenInPeriode voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}")
        demoService.deleteBetalingenInPeriode(hulpvrager, periodeId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/administratie/{administratieId}/reset")
    fun resetGebruikerData(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<String> {
        val (hulpvrager, vrijwilliger) = gebruikerService.checkAccess(administratieId)
        logger.info("PUT DemoController.resetGebruikerData voor ${hulpvrager.naam} door ${vrijwilliger.bijnaam}/${vrijwilliger.subject}")
            demoRepository.resetGebruikerData(hulpvrager.id)
        return ResponseEntity.ok().build()
    }
}
