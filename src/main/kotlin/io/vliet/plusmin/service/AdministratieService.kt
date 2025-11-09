package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Administratie.AdministratieDTO
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class AdministratieService {
    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(gebruiker: Gebruiker, administratiesLijst: List<AdministratieDTO>): List<Administratie> {
        return administratiesLijst.map { administratieDTO ->
            save(gebruiker, administratieDTO)
        }
    }

    fun save(gebruiker: Gebruiker, administratieDTO: AdministratieDTO): Administratie {
        logger.info("administratie: ${administratieDTO.naam} voor gebruiker ${gebruiker.bijnaam}/${gebruiker.subject}")
        val administratieOpt =
            administratieRepository.findAdministratieOpNaamEnGebruiker(administratieDTO.naam, gebruiker)
        val administratie =
            if (administratieOpt != null) {
                administratieOpt
            } else {
                administratieRepository.save(
                    Administratie(
                        naam = administratieDTO.naam,
                        periodeDag = administratieDTO.periodeDag,
                        eigenaar = gebruiker
                    )
                )
            }

        gebruikerRepository.save(gebruiker.fullCopy(administraties = gebruiker.administraties + administratie))

        if (administratieOpt != null) {
            if (administratie.periodeDag != administratieDTO.periodeDag) {
                if (administratieDTO.periodeDag > 28) {
                    logger.warn("Periodedag moet kleiner of gelijk zijn aan 28 (gevraagd: ${administratieDTO.periodeDag})")
                } else {
                    logger.info("Periodedag wordt aangepast voor administratie ${administratie.naam}/${gebruiker.subject} van ${administratie.periodeDag} -> ${administratieDTO.periodeDag}")
//                    periodeService.pasPeriodeDagAan(administratie, administratieDTO)
                    administratieRepository.save(administratie.fullCopy(periodeDag = administratieDTO.periodeDag))
                }
            }
        } else {
            val initielePeriodeStartDatum = if (!administratieDTO.periodes.isNullOrEmpty()) {
                LocalDate.parse(administratieDTO.periodes.sortedBy { it.periodeStartDatum }[0].periodeStartDatum)
            } else {
                periodeService.berekenPeriodeDatums(administratieDTO.periodeDag, LocalDate.now()).first
            }
            periodeService.creeerInitielePeriode(administratie, initielePeriodeStartDatum)
        }

        val bufferRekeningen = rekeningGroepRepository
            .findRekeningGroepenOpSoort(administratie, RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER)
        if (bufferRekeningen.size == 0)
            rekeningService.save(
                administratie,
                RekeningGroep.RekeningGroepDTO(
                    naam = "Buffer",
                    rekeningGroepSoort = RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER.name,
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

        return administratie
    }

}