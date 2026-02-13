package io.vliet.plusmin.controller

import io.vliet.plusmin.domain.Label
import io.vliet.plusmin.domain.PM_LabelBatchInvalidException
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.LabelRepository
import io.vliet.plusmin.service.GebruikerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.dao.DataIntegrityViolationException

@RestController
@RequestMapping("/label")
class LabelController {

    @Autowired
    lateinit var labelRepository: LabelRepository

    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @PostMapping("/{administratieId}")
    fun postLabel(
        @PathVariable("administratieId") administratieId: Long,
        @RequestBody(required = true) namen: List<String>,
    ): ResponseEntity<Any> {
        val eigenaar = gebruikerService.getJwtGebruiker()
        logger.info("PUT LabelController.postLabel door ${eigenaar.bijnaam}/${eigenaar.subject}")
        val administratieOpt = administratieRepository.findById(administratieId)
        if (administratieOpt.isEmpty) {
            return ResponseEntity.badRequest().body(HttpStatus.BAD_REQUEST)
        }
        val administratie = administratieOpt.get()
        var blankCount = 0
        var conflictCount = 0
        for (naam in namen) {
            if (naam.isBlank()) {
                blankCount++
                continue
            }
            val existing = labelRepository.findByAdministratieAndNaam(administratie, naam)
            if (existing != null) {
                conflictCount++
                continue
            }
            try {
                labelRepository.save(Label(0, naam, administratie))
            } catch (e: DataIntegrityViolationException) {
                logger.warn("Duplicate label save prevented for $naam", e)
                conflictCount++
            }
        }
        if (blankCount > 0 || conflictCount > 0) {
            throw PM_LabelBatchInvalidException(listOf(blankCount.toString(), conflictCount.toString()))
        }
        return ResponseEntity.ok().build()
    }
}
