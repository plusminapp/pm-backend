package io.vliet.plusmin.controller

import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.LabelRepository
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.LabelService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/label")
class LabelController {

    @Autowired
    lateinit var labelService: LabelService

    @Autowired
    lateinit var labelRepository: LabelRepository

    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @PostMapping("/{administratieId}")
    fun postLabels(
        @PathVariable("administratieId") administratieId: Long,
        @RequestBody(required = true) namen: List<String>,
    ): ResponseEntity<Any> {
        val eigenaar = gebruikerService.getJwtGebruiker()
        logger.info("POST LabelController.postLabel door ${eigenaar.bijnaam}/${eigenaar.subject}")
        if (labelService.createLabel(administratieId, namen)) return ResponseEntity.badRequest().body(HttpStatus.BAD_REQUEST)
        return ResponseEntity.ok().build()
    }

   @GetMapping("/{administratieId}")
    fun getLabels(
        @PathVariable("administratieId") administratieId: Long,
    ): ResponseEntity<Any> {
        val eigenaar = gebruikerService.getJwtGebruiker()
        logger.info("GET LabelController.getLabel door ${eigenaar.bijnaam}/${eigenaar.subject}")
       val labels = labelRepository.findByAdministratie(administratieId)
        return ResponseEntity.ok(labels.map { it.naam })
    }
}

