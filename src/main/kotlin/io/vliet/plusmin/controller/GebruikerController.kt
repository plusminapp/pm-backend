package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Gebruiker.GebruikerDTO
import io.vliet.plusmin.domain.PM_CreateUserAuthorizationException
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.service.GebruikerService
import io.vliet.plusmin.service.PeriodeService
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/gebruikers")
class GebruikerController {
    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Autowired
    lateinit var gebruikerService: GebruikerService

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET alle gebruikers (alleen voor de COORDINATOR)")
    @RolesAllowed("COORDINATOR")
    @GetMapping("")
    fun getAlleGebruikers(): List<GebruikerDTO> {
        val gebruiker = gebruikerService.getJwtGebruiker()
        logger.info("GET GebruikerController.getAlleGebruikers() voor gebruiker ${gebruiker.email} met rollen ${gebruiker.roles}.")
        return gebruikerRepository.findAll().map { toDTO(it) }
    }

    // Iedereen mag de eigen gebruiker (incl. eventueel gekoppelde hulpvragers) opvragen; ADMIN krijgt iedereen terug als hulpvrager
    @Operation(summary = "GET de gebruiker incl. eventuele hulpvragers op basis van de JWT van een gebruiker")
    @GetMapping("/zelf")
    fun findGebruikerInclusiefHulpvragers(): Gebruiker.GebruikerMetHulpvragersDTO {
        val gebruiker = gebruikerService.getJwtGebruiker()
        logger.info("GET GebruikerController.findHulpvragersVoorVrijwilliger() voor vrijwilliger ${gebruiker.email}.")
        val hulpvragers = if (gebruiker.roles.contains(Gebruiker.Role.ROLE_ADMIN)) {
            gebruikerRepository.findAll().filter { it.id != gebruiker.id }
        } else {
            gebruikerRepository.findHulpvragersVoorVrijwilliger(gebruiker)
        }
        periodeService.checkPeriodesVoorGebruiker(gebruiker)
        hulpvragers.forEach { periodeService.checkPeriodesVoorGebruiker(it) }
        return Gebruiker.GebruikerMetHulpvragersDTO(toDTO(gebruiker), hulpvragers.map { toDTO(it) })
    }

    @PostMapping("")
    fun creeerNieuweGebruiker(@Valid @RequestBody gebruikerList: List<GebruikerDTO>): List<Gebruiker> {
        val gebruiker = gebruikerService.getJwtGebruiker()
        val nieuweGebruikers = gebruikerList.joinToString(", ") { it.bijnaam }
        logger.info(
            "POST GebruikerController.creeerNieuweGebruiker() door vrijwilliger ${gebruiker.bijnaam}: $nieuweGebruikers"
        )
        if (!gebruiker.roles.contains(Gebruiker.Role.ROLE_COORDINATOR)) {
            throw PM_CreateUserAuthorizationException(listOf(gebruiker.bijnaam, nieuweGebruikers))
        }
        return gebruikerService.saveAll(gebruikerList)
    }

    fun toDTO(gebruiker: Gebruiker): GebruikerDTO {
        val periodes: List<Periode> = periodeRepository.getPeriodesVoorGebruiker(gebruiker)
        return GebruikerDTO(
            gebruiker.id,
            gebruiker.email,
            gebruiker.bijnaam,
            gebruiker.periodeDag,
            gebruiker.roles.map { it.toString() },
            gebruiker.vrijwilliger?.email ?: "",
            gebruiker.vrijwilliger?.bijnaam ?: "",
            periodes = periodes.map { it.toDTO() }
        )
    }
}
