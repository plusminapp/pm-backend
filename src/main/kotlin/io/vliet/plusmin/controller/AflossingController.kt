package io.vliet.plusmin.controller

import io.vliet.plusmin.domain.Aflossing.AflossingDTO
import io.vliet.plusmin.domain.PM_AflossingNotFoundException
import io.vliet.plusmin.repository.AflossingRepository
import io.vliet.plusmin.service.GebruikerService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.jvm.optionals.getOrElse

@RestController
@RequestMapping("/aflossingen")
class AflossingController {

    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var aflossingRepository: AflossingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
    
    @PutMapping("/{aflossingId}")
    fun wijzigAflossingDossierNotities(
        @PathVariable("aflossingId") aflossingId: Long,
        @Valid @RequestBody aflossingDTO: AflossingDTO,
    ): ResponseEntity<Any> {
        val aflossingOpt = aflossingRepository.findById(aflossingId)
        val aflossing = aflossingOpt.getOrElse {
            throw PM_AflossingNotFoundException(listOf(aflossingId.toString()))
        }
        val administratie = aflossingRepository.findAdministratieByAflossing(aflossing) ?:
            throw PM_AflossingNotFoundException(listOf(aflossingId.toString()))
        val (_, _) = gebruikerService.checkAccess(administratie.id)
        aflossingRepository.save(aflossing.fullCopy(
            dossierNummer = aflossingDTO.dossierNummer,
            notities = aflossingDTO.notities
        ))
        return ResponseEntity.ok().build()
    }

}
