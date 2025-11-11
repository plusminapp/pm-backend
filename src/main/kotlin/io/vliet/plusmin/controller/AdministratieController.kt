package io.vliet.plusmin.controller

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Administratie.AdministratieDTO
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.PM_EigenaarAuthorizationException
import io.vliet.plusmin.domain.PM_EigenaarZichzelfAuthorizationException
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.AdministratieService
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.PeriodeService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
    
    @PostMapping("")
    fun creeerNieuweAdministratie(@Valid @RequestBody administratieList: List<AdministratieDTO>): List<Administratie> {
        val eigenaar = gebruikerService.getJwtGebruiker()
        val nieuweAdministraties = administratieList.joinToString(", ") { it.naam }
        logger.info(
            "POST AdministratieController.creeerNieuweAdministratie() door gebruiker ${eigenaar.bijnaam}: $nieuweAdministraties"
        )
        return administratieService.saveAll(eigenaar, administratieList)
    }

    @PutMapping("{administratieId}/gebruiker/{gebruikerId}/toegang-verstrekken")
    fun toegangVerstrekken(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("gebruikerId") toegangGebruikerId: Long,
    ) {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        if (!administratie.eigenaar.id.equals(gebruiker.id)) {
            throw PM_EigenaarAuthorizationException(listOf(gebruiker.bijnaam, toegangGebruikerId.toString(), administratie.naam))
        }
        return administratieService.toegangVerstrekken(toegangGebruikerId, administratie)
    }

    @PutMapping("{administratieId}/gebruiker/{gebruikerId}/toegang-intrekken")
    fun toegangIntrekken(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("gebruikerId") toegangGebruikerId: Long,
    ) {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        if (!administratie.eigenaar.id.equals(gebruiker.id)) {
            throw PM_EigenaarAuthorizationException(listOf(gebruiker.bijnaam, toegangGebruikerId.toString(), administratie.naam))
        }
        if (toegangGebruikerId.equals(gebruiker.id)) {
            throw PM_EigenaarZichzelfAuthorizationException(
                listOf(
                    gebruiker.bijnaam,
                    administratie.naam
                )
            )
        }
        return administratieService.toegangIntrekken(toegangGebruikerId, administratie)
    }

    @PutMapping("{administratieId}/gebruiker/{gebruikerId}/eigenaar-overdagen")
    fun eigenaarOverdragen(
        @PathVariable("administratieId") administratieId: Long,
        @PathVariable("gebruikerId") toegangGebruikerId: Long,
    ) {
        val (administratie, gebruiker) = gebruikerService.checkAccess(administratieId)
        if (!administratie.eigenaar.id.equals(gebruiker.id)) {
            throw PM_EigenaarAuthorizationException(listOf(gebruiker.bijnaam, toegangGebruikerId.toString(), administratie.naam))
        }
        return administratieService.eigenaarOverdragen(toegangGebruikerId, administratie)
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
