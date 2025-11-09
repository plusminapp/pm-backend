package io.vliet.plusmin.controller

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Administratie.AdministratieDTO
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.AdministratieService
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.PeriodeService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/administraties")
class AdministratieController {
    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var administratieService: AdministratieService

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
    
    @PostMapping("")
    fun creeerNieuweAdministratie(@Valid @RequestBody administratieList: List<AdministratieDTO>): List<Administratie> {
        val gebruiker = gebruikerService.getJwtGebruiker()
        val nieuweAdministraties = administratieList.joinToString(", ") { it.naam }
        logger.info(
            "POST AdministratieController.creeerNieuweAdministratie() door gebruiker ${gebruiker.bijnaam}: $nieuweAdministraties"
        )
        return administratieService.saveAll(gebruiker, administratieList)
    }

    fun toDTO(administratie: Administratie): AdministratieDTO {
        return AdministratieDTO(
            administratie.id,
            administratie.naam,
            administratie.periodeDag,
            administratie.eigenaar.bijnaam,
            administratie.eigenaar.subject,
            periodeRepository.getPeriodesVoorAdministrtatie(administratie).map { it.toDTO() },
        )
    }
}
