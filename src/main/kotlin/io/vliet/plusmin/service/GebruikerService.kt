package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Gebruiker.GebruikerDTO
import io.vliet.plusmin.domain.Gebruiker.Role
import io.vliet.plusmin.domain.PM_AdministratieNotFoundException
import io.vliet.plusmin.domain.PM_GeneralAuthorizationException
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.GebruikerRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class GebruikerService {
    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getJwtGebruiker(): Gebruiker {
        val jwt = SecurityContextHolder.getContext().authentication.principal as Jwt
        val subject = jwt.claims["sub"] as String
        // de gebruiker wordt in de configuration/WebSecurity.kt aangemaakt als ie niet bestaat
        return gebruikerRepository.findBySubject(subject)!!
    }

    fun checkAccess(administratieId: Long): Pair<Administratie, Gebruiker> {
        val administratieOpt = administratieRepository.findById(administratieId)
        if (administratieOpt.isEmpty)
            throw PM_AdministratieNotFoundException(listOf(administratieId.toString()))
        val administratie = administratieOpt.get()

        val gebruiker = getJwtGebruiker()
        logger.info("checkAccess administratie ${administratie.naam} voor gebruiker ${gebruiker.bijnaam}/${gebruiker.subject}: " +
                "adminId: ${administratie.id}  " +
                "administraties ${gebruiker.administraties.joinToString { it.id.toString() }}  " +
                "toegang: ${gebruiker.administraties.map { it.id }.contains(administratie.id)}")
        if (!(gebruiker.administraties.map { it.id }.contains(administratie.id) ||
                    gebruiker.roles.contains(Role.ROLE_ADMIN))
        )
            throw PM_GeneralAuthorizationException(listOf(gebruiker.bijnaam, administratie.naam))
        return Pair(administratie, gebruiker)
    }

    fun saveAll(gebruikersLijst: List<GebruikerDTO>): List<Gebruiker> {
        return gebruikersLijst.map { gebruikerDTO ->
            save(gebruikerDTO)
        }
    }

    fun save(gebruikerDTO: GebruikerDTO): Gebruiker {
        logger.info("gebruiker: ${gebruikerDTO.bijnaam}/${gebruikerDTO.subject}")
        val gebruikerOpt = gebruikerRepository.findBySubject(gebruikerDTO.subject)
        val gebruiker =
            if (gebruikerOpt != null) {
                gebruikerRepository.save(
                    gebruikerOpt.fullCopy(
                        // periodeDag nog: kan nog gewijzigd moeten worden (zie verderop)
                        bijnaam = gebruikerDTO.bijnaam,
                        roles = gebruikerDTO.roles.map { enumValueOf<Role>(it) }.toMutableSet(),
                    )
                )
            } else {
                gebruikerRepository.save(
                    Gebruiker(
                        subject = gebruikerDTO.subject,
                        bijnaam = gebruikerDTO.bijnaam,
                        roles = gebruikerDTO.roles.map { enumValueOf<Role>(it) }.toMutableSet(),
                    )
                )
            }
        return gebruiker
    }
}