package io.vliet.plusmin.service

import io.vliet.plusmin.controller.AdministratieWrapper
import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Administratie.AdministratieDTO
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.PM_AdministratieBestaatAlException
import io.vliet.plusmin.domain.PM_GebruikerNotFoundException
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import jakarta.persistence.EntityManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.plus

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
    lateinit var betalingService: BetalingService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var entityManager: EntityManager
    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(eigenaar: Gebruiker, administratiesLijst: List<AdministratieDTO>): List<Administratie> {
        return administratiesLijst.map { administratieDTO ->
            save(eigenaar, administratieDTO)
        }
    }

    @Transactional
    fun save(eigenaar: Gebruiker, administratieDTO: AdministratieDTO): Administratie {
        logger.info("administratie: ${administratieDTO.naam} voor gebruiker ${eigenaar.bijnaam}/${eigenaar.subject} vandaag=${administratieDTO.vandaag} periodeDag=${administratieDTO.periodeDag}")
        val administratieOpt =
            administratieRepository.findAdministratieOpNaamEnGebruiker(administratieDTO.naam, eigenaar)
        val vandaag = if (administratieDTO.vandaag.isNullOrEmpty())
            null
        else
            LocalDate.parse(administratieDTO.vandaag)
        val administratie =
            if (administratieOpt != null) {
                administratieOpt
            } else {
                administratieRepository.save(
                    Administratie(
                        naam = administratieDTO.naam,
                        periodeDag = administratieDTO.periodeDag,
                        vandaag = vandaag,
                        eigenaar = eigenaar
                    )
                )
            }

        if (administratieOpt == null) {
            gebruikerRepository.save(eigenaar.fullCopy(administraties = eigenaar.administraties + administratie))
            val initielePeriodeStartDatum = if (!administratieDTO.periodes.isNullOrEmpty()) {
                LocalDate.parse(administratieDTO.periodes.sortedBy { it.periodeStartDatum }[0].periodeStartDatum)
            } else {
                periodeService.berekenPeriodeDatums(administratieDTO.periodeDag, vandaag ?: LocalDate.now()).first
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

    fun toegangVerstrekken(gebruikerId: Long, administratie: Administratie) {
        val gebruikerOpt = gebruikerRepository.findById(gebruikerId)
        if (gebruikerOpt.isEmpty) {
            throw PM_GebruikerNotFoundException(listOf(gebruikerId.toString()))
        }
        val gebruiker = gebruikerOpt.get()
        val administraties = if (gebruiker.administraties.any { it.id == administratie.id })
            gebruiker.administraties
        else
            (gebruiker.administraties + administratie)
        gebruikerRepository.save(gebruiker.fullCopy(administraties = administraties))
    }

    fun toegangIntrekken(gebruikerId: Long, administratie: Administratie) {
        val gebruikerOpt = gebruikerRepository.findById(gebruikerId)
        if (gebruikerOpt.isEmpty) {
            throw PM_GebruikerNotFoundException(listOf(gebruikerId.toString()))
        }
        val gebruiker = gebruikerOpt.get()
        gebruikerRepository.save(gebruiker.fullCopy(administraties = gebruiker.administraties.filter { it.id != administratie.id }))
    }

    fun eigenaarOverdragen(gebruikerId: Long, administratie: Administratie) {
        val gebruikerOpt = gebruikerRepository.findById(gebruikerId)
        if (gebruikerOpt.isEmpty) {
            throw PM_GebruikerNotFoundException(listOf(gebruikerId.toString()))
        }
        val nieuweEigenaar = gebruikerOpt.get()
        val administraties = if (nieuweEigenaar.administraties.any { it.id == administratie.id })
            nieuweEigenaar.administraties
        else
            (nieuweEigenaar.administraties + administratie)
        gebruikerRepository.save(nieuweEigenaar.fullCopy(administraties = administraties))
        administratieRepository.save(administratie.fullCopy(eigenaar = nieuweEigenaar))
    }


    fun laadAdministratie(administratieWrapper: AdministratieWrapper, eigenaar: Gebruiker) {
        val administratieDTO = administratieWrapper.administratie
        val administratieBestaand = administratieRepository
            .findAdministratieOpNaamEnGebruiker(administratieWrapper.administratie.naam, eigenaar)
        if (administratieBestaand != null && !administratieWrapper.overschrijfBestaande) {
            throw PM_AdministratieBestaatAlException(listOf(administratieDTO.naam))
        }
        val opgeschoondeEigenaar = if (administratieBestaand != null) {
            verwijderAdministratie(administratieBestaand.id, eigenaar)
            eigenaar.fullCopy(
                administraties = eigenaar.administraties.filter { it.id != administratieBestaand.id }
            )
        } else eigenaar
        maakNieuweAdministratie(administratieWrapper, opgeschoondeEigenaar)
    }

    @Transactional
    fun verwijderAdministratie(administratieId: Long, gebruiker: Gebruiker) {
        administratieRepository.deleteAdministratie(administratieId)
//        gebruikerRepository.save(gebruiker.fullCopy(
//            administraties = gebruiker.administraties.filter { it.id != administratieId }
//        ))
    }

    @Transactional
    fun maakNieuweAdministratie(administratieWrapper: AdministratieWrapper, eigenaar: Gebruiker) {
        val administratie = save(eigenaar, administratieWrapper.administratie)
        rekeningService.saveAll(administratie, administratieWrapper.rekeningGroepen)
        betalingService.creeerBetalingLijst(administratie, administratieWrapper.betalingen)
        if (administratie.vandaag != null) {
            betalingRepository.hideAllByAdministratie(administratie)
        }
    }
}