package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Gebruiker.GebruikerDTO
import io.vliet.plusmin.domain.Gebruiker.Role
import io.vliet.plusmin.domain.PM_AdministratieNotFoundException
import io.vliet.plusmin.domain.PM_GeneralAuthorizationException
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.domain.RekeningGroep.RekeningGroepSoort
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class GebruikerService {
    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var rekeningService: RekeningService

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
        if (!gebruiker.administraties.contains(administratie) &&
            !gebruiker.roles.contains(Role.ROLE_ADMIN)
        ) throw PM_GeneralAuthorizationException(listOf(gebruiker.bijnaam, administratie.naam))
        return Pair(administratie, gebruiker)
    }

    fun saveAll(gebruikersLijst: List<GebruikerDTO>): List<Gebruiker> {
        return gebruikersLijst.map { gebruikerDTO ->
            save(gebruikerDTO)
        }
    }

    fun save(gebruikerDTO: GebruikerDTO): Gebruiker {
        logger.info("gebruiker: ${gebruikerDTO.email}/${gebruikerDTO.subject}")
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
                        email = gebruikerDTO.email,
                        bijnaam = gebruikerDTO.bijnaam,
                        roles = gebruikerDTO.roles.map { enumValueOf<Role>(it) }.toMutableSet(),
                    )
                )
            }

//        if (gebruikerOpt != null) {
//            if (Gebruiker.periodeDag != gebruikerDTO.periodeDag) {
//                if (gebruikerDTO.periodeDag > 28) {
//                    logger.warn("Periodedag moet kleiner of gelijk zijn aan 28 (gevraagd: ${gebruikerDTO.periodeDag})")
//                } else {
//                    logger.info("Periodedag wordt aangepast voor gebruiker ${gebruiker.bijnaam}/${gebruiker.subject} van ${Gebruiker.periodeDag} -> ${gebruikerDTO.periodeDag}")
////                    periodeService.pasPeriodeDagAan(Gebruiker, gebruikerDTO)
//                    gebruikerRepository.save(Gebruiker.fullCopy(periodeDag = gebruikerDTO.periodeDag))
//                }
//            }
//        } else {
//            val initielePeriodeStartDatum: LocalDate = if (!gebruikerDTO.periodes.isNullOrEmpty()) {
//                LocalDate.parse(gebruikerDTO.periodes.sortedBy { it.periodeStartDatum }[0].periodeStartDatum)
//            } else {
//                periodeService.berekenPeriodeDatums(gebruikerDTO.periodeDag, LocalDate.now()).first
//            }
////            TODO periodeService.creeerInitielePeriode(Gebruiker, initielePeriodeStartDatum)
//        }
//
//        val bufferRekeningen = rekeningGroepRepository
//            .findRekeningGroepenOpSoort(Gebruiker, RekeningGroepSoort.RESERVERING_BUFFER)
//        if (bufferRekeningen.size == 0)
//            rekeningService.save(
//                Gebruiker,
//                RekeningGroep.RekeningGroepDTO(
//                    naam = "Buffer",
//                    rekeningGroepSoort = RekeningGroepSoort.RESERVERING_BUFFER.name,
//                    sortOrder = 0,
//                    rekeningen = listOf(
//                        Rekening.RekeningDTO(
//                            naam = "Buffer IN",
//                            saldo = BigDecimal(0),
//                            rekeningGroepNaam = "Buffer",
//                            budgetAanvulling = Rekening.BudgetAanvulling.IN
//                        )
//                    )
//                ),
//                syscall = true
//            )

        return gebruiker
    }
}