package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.*
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.Integer.parseInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class BetalingService {
    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun creeerBetalingLijst(administratie: Administratie, betalingenLijst: List<BetalingDTO>): List<BetalingDTO> {
        return betalingenLijst.map { betalingDTO ->
            creeerBetaling(administratie, betalingDTO)
        }
    }

    fun creeerBetaling(administratie: Administratie, betalingDTO: BetalingDTO): BetalingDTO {
        val boekingsDatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val periode = periodeRepository.getPeriodeAdministratieEnDatum(administratie.id, boekingsDatum)
        if (periode == null || (periode.periodeStatus != Periode.PeriodeStatus.OPEN && periode.periodeStatus != Periode.PeriodeStatus.HUIDIG)) {
            throw PM_NoOpenPeriodException(
                listOf(
                    boekingsDatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    administratie.naam
                )
            )
        }
        val bron = rekeningRepository.findRekeningAdministratieEnNaam(administratie, betalingDTO.bron)
            ?: throw PM_RekeningNotFoundException(listOf(betalingDTO.bron, administratie.naam))
        val bestemming = rekeningRepository.findRekeningAdministratieEnNaam(administratie, betalingDTO.bestemming)
            ?: throw PM_RekeningNotFoundException(listOf(betalingDTO.bestemming, administratie.naam))

        val getransformeerdeBoeking = transformeerVanDtoBoeking(
            BetalingsSoort.valueOf(betalingDTO.betalingsSoort), Boeking(bron, bestemming)
        )
        val sortOrder = berekenSortOrder(administratie, boekingsDatum)
        logger.debug("Nieuwe betaling ${betalingDTO.omschrijving} voor ${administratie.naam}")
        val betaling = Betaling(
            administratie = administratie,
            boekingsdatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
            bedrag = betalingDTO.bedrag,
            omschrijving = betalingDTO.omschrijving,
            betalingsSoort = BetalingsSoort.valueOf(betalingDTO.betalingsSoort),
            bron = getransformeerdeBoeking.first?.bron,
            bestemming = getransformeerdeBoeking.first?.bestemming,
            reserveringBron = getransformeerdeBoeking.second?.bron,
            reserveringBestemming = getransformeerdeBoeking.second?.bestemming,
            sortOrder = sortOrder,
        )
        return betalingRepository.save(betaling).toDTO()
    }

    fun berekenSortOrder(administratie: Administratie, boekingsDatum: LocalDate): String {
        val laatsteSortOrder: String? = betalingRepository.findLaatsteSortOrder(administratie, boekingsDatum)
        val sortOrderDatum = boekingsDatum.toString().replace("-", "")
        return if (laatsteSortOrder == null) "$sortOrderDatum.100"
        else {
            val sortOrderTeller = (parseInt(laatsteSortOrder.split(".")[1]) + 10).toString()
            "$sortOrderDatum.$sortOrderTeller"
        }
    }

    fun update(oldBetaling: Betaling, newBetalingDTO: BetalingDTO): Betaling {
        val gebruiker = oldBetaling.administratie
        val boekingsDatum = LocalDate.parse(newBetalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val periode = periodeRepository.getPeriodeAdministratieEnDatum(gebruiker.id, boekingsDatum)
        if (periode == null || (periode.periodeStatus != Periode.PeriodeStatus.OPEN && periode.periodeStatus != Periode.PeriodeStatus.HUIDIG)) {
            throw PM_NoOpenPeriodException(
                listOf(
                    boekingsDatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    gebruiker.naam
                )
            )
        }
        val bron = rekeningRepository.findRekeningAdministratieEnNaam(gebruiker, newBetalingDTO.bron)
            ?: throw PM_RekeningNotFoundException(listOf(newBetalingDTO.bron, gebruiker.naam))
        val bestemming = rekeningRepository.findRekeningAdministratieEnNaam(gebruiker, newBetalingDTO.bestemming)
            ?: throw PM_RekeningNotFoundException(listOf(newBetalingDTO.bestemming, gebruiker.naam))

        val getransformeerdeBoeking = transformeerVanDtoBoeking(
            BetalingsSoort.valueOf(newBetalingDTO.betalingsSoort), Boeking(bron, bestemming)
        )
        logger.debug("Update betaling ${oldBetaling.id}/${newBetalingDTO.omschrijving} voor ${gebruiker.naam} ")
        val newBetaling = oldBetaling.fullCopy(
            boekingsdatum = boekingsDatum,
            bedrag = newBetalingDTO.bedrag,
            omschrijving = newBetalingDTO.omschrijving,
            betalingsSoort = BetalingsSoort.valueOf(newBetalingDTO.betalingsSoort),
            bron = getransformeerdeBoeking.first?.bron,
            bestemming = getransformeerdeBoeking.first?.bestemming,
            reserveringBron = getransformeerdeBoeking.second?.bron,
            reserveringBestemming = getransformeerdeBoeking.second?.bestemming,
        )
        return betalingRepository.save(newBetaling)
    }

    /*
     *    Transformeert een DTO-Boeking naar betaling en reservering boekingen, afhankelijk van de betalingssoort.
     *    Zie ook https://docs.google.com/spreadsheets/d/1erhLtz1Kp1ZiEvSCOSyJRTElepPDDEDiZdDrYggmm0o/edit
     */
    fun transformeerVanDtoBoeking(
        betalingsSoort: BetalingsSoort,
        dtoBoeking: Boeking
    ): Pair<Boeking?, Boeking?> {
        return when (betalingsSoort) {
            BetalingsSoort.INKOMSTEN, BetalingsSoort.UITGAVEN, BetalingsSoort.BESTEDEN, BetalingsSoort.AFLOSSEN, BetalingsSoort.INTERN ->
                Pair(dtoBoeking, null)

            BetalingsSoort.RESERVEREN -> Pair(null, dtoBoeking)

        }
    }

    fun valideerBetalingenVoorGebruiker(administratie: Administratie): List<Betaling> {
        val betalingenLijst = betalingRepository.findAllByAdministratie(administratie).filter { betaling: Betaling ->
            val periode = periodeRepository.getPeriodeAdministratieEnDatum(administratie.id, betaling.boekingsdatum)
            periode != null && betaling.bron != null && betaling.bestemming != null && (!betaling.bron.rekeningIsGeldigInPeriode(
                periode
            ) || !betaling.bestemming.rekeningIsGeldigInPeriode(periode))
        }
        return betalingenLijst
    }
}