package io.vliet.plusmin.controller

import io.vliet.plusmin.repository.DemoRepository
import io.vliet.plusmin.service.DemoService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
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
    lateinit var gebruikerController: GebruikerController

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

    @PutMapping("/hulpvrager/{hulpvragerId}/configureer")
    fun configureerPeriode(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT DemoController.configureerPeriode voor ${hulpvrager.email} door ${vrijwilliger.email}")
        try {
            demoService.configureerDemoBetalingen(hulpvrager)
        } catch (e: Exception) {
            logger.error("Fout bij demo configuratie voor hulpvrager ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body("Fout bij demo configuratie : ${e.message}")
        }
        return ResponseEntity.ok().body("Demo voor hulpvrager ${hulpvrager.email} is succesvol geconfigureerd.")    }

    @DeleteMapping("/hulpvrager/{hulpvragerId}/verwijderVanPeriode/{periodeId}")
    fun deleteBetalingenInPeriode(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @PathVariable("periodeId") periodeId: Long,
    ): ResponseEntity<String> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("DELETE DemoController.deleteBetalingenInPeriode voor ${hulpvrager.email} door ${vrijwilliger.email}")
        demoService.deleteBetalingenInPeriode(hulpvrager, periodeId)
        return ResponseEntity.ok().body("Betalingen verwijderd.")
    }

    @PutMapping("/hulpvrager/{hulpvragerId}/reset")
    fun resetGebruikerData(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): ResponseEntity<String> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT DemoController.resetGebruikerData voor ${hulpvrager.email} door ${vrijwilliger.email}")
        try {
            demoRepository.resetGebruikerData(hulpvrager.id)
        } catch (e: Exception) {
            logger.error("Fout bij reset gebruiker data voor ${hulpvrager.email}: ${e.message}")
            return ResponseEntity.badRequest().body("Fout bij reset gebruiker data : ${e.message}")
        }
        return ResponseEntity.ok().body("Gebruiker data voor ${hulpvrager.email} is succesvol gereset.")
    }
}
