package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Gebruiker.GebruikerDTO
import io.vliet.plusmin.domain.Gebruiker.Role
import io.vliet.plusmin.domain.PM_GeneralAuthorizationException
import io.vliet.plusmin.domain.PM_HulpvragerNotFoundException
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.domain.RekeningGroep.RekeningGroepSoort
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
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getJwtGebruiker(): Gebruiker {
        val jwt = SecurityContextHolder.getContext().authentication.principal as Jwt
        val email = jwt.claims["username"] as String
        // de gebruiker wordt in de configuration/WebSecurity.kt aangemaakt als ie niet bestaat
        return gebruikerRepository.findByEmail(email)!!
    }

    fun checkAccess(hulpvragerId: Long): Pair<Gebruiker, Gebruiker> {
        val hulpvragerOpt = gebruikerRepository.findById(hulpvragerId)
        if (hulpvragerOpt.isEmpty)
            throw PM_HulpvragerNotFoundException(listOf(hulpvragerId.toString()))
        val hulpvrager = hulpvragerOpt.get()

        val vrijwilliger = getJwtGebruiker()
        if (hulpvrager.id != vrijwilliger.id &&
            hulpvrager.vrijwilliger?.id != vrijwilliger.id &&
            !vrijwilliger.roles.contains(Role.ROLE_ADMIN)
        ) throw PM_GeneralAuthorizationException(listOf(vrijwilliger.bijnaam, hulpvrager.bijnaam))
        return Pair(hulpvrager, vrijwilliger)
    }

    fun saveAll(gebruikersLijst: List<GebruikerDTO>): List<Gebruiker> {
        return gebruikersLijst.map { gebruikerDTO ->
            save(gebruikerDTO)
        }
    }

    fun save(gebruikerDTO: GebruikerDTO): Gebruiker {
        val vrijwilliger = if (gebruikerDTO.vrijwilligerEmail.isNotEmpty()) {
            gebruikerRepository.findByEmail(gebruikerDTO.vrijwilligerEmail)
        } else null
        logger.info("gebruiker: ${gebruikerDTO.email}, vrijwilliger: ${vrijwilliger?.email}")
        val gebruikerOpt = gebruikerRepository.findByEmail(gebruikerDTO.email)
        val gebruiker =
            if (gebruikerOpt != null) {
                gebruikerRepository.save(
                    gebruikerOpt.fullCopy(
                        // periodeDag nog: kan nog gewijzigd moeten worden (zie verderop)
                        bijnaam = gebruikerDTO.bijnaam,
                        roles = gebruikerDTO.roles.map { enumValueOf<Role>(it) }.toMutableSet(),
                        vrijwilliger = vrijwilliger,
                    )
                )
            } else {
                gebruikerRepository.save(
                    Gebruiker(
                        email = gebruikerDTO.email,
                        bijnaam = gebruikerDTO.bijnaam,
                        periodeDag = gebruikerDTO.periodeDag,
                        roles = gebruikerDTO.roles.map { enumValueOf<Role>(it) }.toMutableSet(),
                        vrijwilliger = vrijwilliger,
                    )
                )
            }

        if (gebruikerOpt != null) {
            if (gebruiker.periodeDag != gebruikerDTO.periodeDag) {
                if (gebruikerDTO.periodeDag > 28) {
                    logger.warn("Periodedag moet kleiner of gelijk zijn aan 28 (gevraagd: ${gebruikerDTO.periodeDag})")
                } else {
                    logger.info("Periodedag wordt aangepast voor gebruiker ${gebruiker.email} van ${gebruiker.periodeDag} -> ${gebruikerDTO.periodeDag}")
                    periodeService.pasPeriodeDagAan(gebruiker, gebruikerDTO)
                    gebruikerRepository.save(gebruiker.fullCopy(periodeDag = gebruikerDTO.periodeDag))
                }
            }
        } else {
            val initielePeriodeStartDatum: LocalDate = if (!gebruikerDTO.periodes.isNullOrEmpty()) {
                LocalDate.parse(gebruikerDTO.periodes.sortedBy { it.periodeStartDatum }[0].periodeStartDatum)
            } else {
                periodeService.berekenPeriodeDatums(gebruikerDTO.periodeDag, LocalDate.now()).first
            }
            periodeService.creeerInitielePeriode(gebruiker, initielePeriodeStartDatum)
        }

        val bufferRekeningen = rekeningGroepRepository
            .findRekeningGroepenOpSoort(gebruiker, RekeningGroepSoort.RESERVERING_BUFFER)
        if (bufferRekeningen.size == 0)
            rekeningService.save(
                gebruiker,
                RekeningGroep.RekeningGroepDTO(
                    naam = "Buffer",
                    rekeningGroepSoort = RekeningGroepSoort.RESERVERING_BUFFER.name,
                    sortOrder = 0,
                    rekeningen = listOf(
                        Rekening.RekeningDTO(
                            naam = "Buffer IN",
                            saldo = BigDecimal(0),
                            rekeningGroepNaam = "Buffer",
                            budgetAanvulling = Rekening.BudgetAanvulling.IN
                        )
                    )
                ),
                syscall = true
            )

        return gebruiker
    }
}