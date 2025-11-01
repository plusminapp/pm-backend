package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.domain.Betaling.Boeking
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.PM_BufferRekeningNotFoundException
import io.vliet.plusmin.domain.PM_NoOpenPeriodException
import io.vliet.plusmin.domain.PM_RekeningNotFoundException
import io.vliet.plusmin.domain.PM_RekeningNotLinkedException
import io.vliet.plusmin.domain.Periode
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

    fun creeerBetalingLijst(gebruiker: Gebruiker, betalingenLijst: List<BetalingDTO>): List<BetalingDTO> {
        return betalingenLijst.map { betalingDTO ->
            creeerBetaling(gebruiker, betalingDTO)
        }
    }

    fun creeerBetaling(gebruiker: Gebruiker, betalingDTO: BetalingDTO): BetalingDTO {
        val boekingsDatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val periode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, boekingsDatum)
        if (periode == null || (periode.periodeStatus != Periode.PeriodeStatus.OPEN && periode.periodeStatus != Periode.PeriodeStatus.HUIDIG)) {
            throw PM_NoOpenPeriodException(
                listOf(
                    boekingsDatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    gebruiker.bijnaam
                )
            )
        }
        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, betalingDTO.bron)
            ?: throw PM_RekeningNotFoundException(listOf(betalingDTO.bron, gebruiker.bijnaam))
        val bestemming = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, betalingDTO.bestemming)
            ?: throw PM_RekeningNotFoundException(listOf(betalingDTO.bestemming, gebruiker.bijnaam))

        val getransformeerdeBoeking = transformeerVanDtoBoeking(
            Betaling.BetalingsSoort.valueOf(betalingDTO.betalingsSoort), Boeking(bron, bestemming)
        )
        val sortOrder = berekenSortOrder(gebruiker, boekingsDatum)
        logger.info("Nieuwe betaling ${betalingDTO.omschrijving} voor ${gebruiker.bijnaam}")
        val betaling = Betaling(
            gebruiker = gebruiker,
            boekingsdatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
            bedrag = betalingDTO.bedrag,
            omschrijving = betalingDTO.omschrijving,
            betalingsSoort = Betaling.BetalingsSoort.valueOf(betalingDTO.betalingsSoort),
            bron = getransformeerdeBoeking.first?.bron,
            bestemming = getransformeerdeBoeking.first?.bestemming,
            reserveringBron = getransformeerdeBoeking.second?.bron,
            reserveringBestemming = getransformeerdeBoeking.second?.bestemming,
            sortOrder = sortOrder,
        )
        return betalingRepository.save(betaling).toDTO()
    }

    fun berekenSortOrder(gebruiker: Gebruiker, boekingsDatum: LocalDate): String {
        val laatsteSortOrder: String? = betalingRepository.findLaatsteSortOrder(gebruiker, boekingsDatum)
        val sortOrderDatum = boekingsDatum.toString().replace("-", "")
        return if (laatsteSortOrder == null) "$sortOrderDatum.100"
        else {
            val sortOrderTeller = (parseInt(laatsteSortOrder.split(".")[1]) + 10).toString()
            "$sortOrderDatum.$sortOrderTeller"
        }
    }

    fun update(oldBetaling: Betaling, newBetalingDTO: BetalingDTO): Betaling {
        val gebruiker = oldBetaling.gebruiker
        val boekingsDatum = LocalDate.parse(newBetalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val periode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, boekingsDatum)
        if (periode == null || (periode.periodeStatus != Periode.PeriodeStatus.OPEN && periode.periodeStatus != Periode.PeriodeStatus.HUIDIG)) {
            throw PM_NoOpenPeriodException(
                listOf(
                    boekingsDatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    gebruiker.bijnaam
                )
            )
        }
        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newBetalingDTO.bron)
            ?: throw PM_RekeningNotFoundException(listOf(newBetalingDTO.bron, gebruiker.bijnaam))
        val bestemming = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newBetalingDTO.bestemming)
            ?: throw PM_RekeningNotFoundException(listOf(newBetalingDTO.bestemming, gebruiker.bijnaam))

        val getransformeerdeBoeking = transformeerVanDtoBoeking(
            Betaling.BetalingsSoort.valueOf(newBetalingDTO.betalingsSoort), Boeking(bron, bestemming)
        )
        logger.info("Update betaling ${oldBetaling.id}/${newBetalingDTO.omschrijving} voor ${gebruiker.bijnaam} ")
        val newBetaling = oldBetaling.fullCopy(
            boekingsdatum = boekingsDatum,
            bedrag = newBetalingDTO.bedrag,
            omschrijving = newBetalingDTO.omschrijving,
            betalingsSoort = Betaling.BetalingsSoort.valueOf(newBetalingDTO.betalingsSoort),
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
        betalingsSoort: Betaling.BetalingsSoort,
        dtoBoeking: Boeking
    ): Pair<Boeking?, Boeking?> {
        val bufferRekening = rekeningRepository.findBufferRekeningVoorGebruiker(dtoBoeking.bron.rekeningGroep.gebruiker)
            ?: throw PM_BufferRekeningNotFoundException(listOf(dtoBoeking.bron.rekeningGroep.gebruiker.bijnaam))
        val gekoppeldeSpaarRekening =
            rekeningRepository
                .findSpaarPotRekeningenGebruiker(dtoBoeking.bron.rekeningGroep.gebruiker)
                .firstOrNull() // gesorteerd op sortOrder
                ?: throw PM_BufferRekeningNotFoundException(listOf(dtoBoeking.bron.rekeningGroep.gebruiker.bijnaam))

        return when (betalingsSoort) {
            Betaling.BetalingsSoort.INKOMSTEN ->
                if (dtoBoeking.bestemming.rekeningGroep.rekeningGroepSoort == io.vliet.plusmin.domain.RekeningGroep.RekeningGroepSoort.SPAARREKENING)
                    Pair(
                        dtoBoeking, Boeking(
                            dtoBoeking.bron,
                            gekoppeldeSpaarRekening
                        )
                    )
                else
                    Pair(dtoBoeking, Boeking(dtoBoeking.bron, bufferRekening))

            Betaling.BetalingsSoort.UITGAVEN, Betaling.BetalingsSoort.BESTEDEN, Betaling.BetalingsSoort.AFLOSSEN -> Pair(
                dtoBoeking, null
            )

            Betaling.BetalingsSoort.SPAREN -> Pair(
                Boeking(
                    dtoBoeking.bron,
                    dtoBoeking.bestemming.gekoppeldeRekening
                        ?: throw PM_RekeningNotLinkedException(
                            listOf(
                                dtoBoeking.bestemming.naam,
                                dtoBoeking.bron.rekeningGroep.gebruiker.email
                            )
                        )
                ), Boeking(
                    bufferRekening, dtoBoeking.bestemming
                )
            )

            Betaling.BetalingsSoort.OPNEMEN -> Pair(
                Boeking(
                    dtoBoeking.bron.gekoppeldeRekening
                        ?: throw PM_RekeningNotLinkedException(
                            listOf(
                                dtoBoeking.bron.naam,
                                dtoBoeking.bron.rekeningGroep.gebruiker.email
                            )
                        ),
                    dtoBoeking.bestemming,
                ),
                Boeking(
                    dtoBoeking.bron, dtoBoeking.bron
                )
            )

            Betaling.BetalingsSoort.TERUGSTORTEN -> Pair(
                Boeking(
                    dtoBoeking.bron,
                    dtoBoeking.bestemming.gekoppeldeRekening
                        ?: throw PM_RekeningNotLinkedException(
                            listOf(
                                dtoBoeking.bron.naam,
                                dtoBoeking.bron.rekeningGroep.gebruiker.email
                            )
                        ),
                ),
                Boeking(
                    dtoBoeking.bestemming, dtoBoeking.bestemming
                )
            )

            Betaling.BetalingsSoort.INCASSO_CREDITCARD, Betaling.BetalingsSoort.OPNEMEN_CONTANT, Betaling.BetalingsSoort.STORTEN_CONTANT -> Pair(
                dtoBoeking, null
            )

            Betaling.BetalingsSoort.P2P, Betaling.BetalingsSoort.SP2SP -> Pair(null, dtoBoeking)

            Betaling.BetalingsSoort.P2SP, Betaling.BetalingsSoort.SP2P -> Pair(
                Boeking(
                    dtoBoeking.bron.gekoppeldeRekening
                        ?: throw PM_RekeningNotLinkedException(
                            listOf(
                                dtoBoeking.bron.naam,
                                dtoBoeking.bron.rekeningGroep.gebruiker.email
                            )
                        ),
                    dtoBoeking.bestemming.gekoppeldeRekening
                        ?: throw PM_RekeningNotLinkedException(
                            listOf(
                                dtoBoeking.bestemming.naam,
                                dtoBoeking.bron.rekeningGroep.gebruiker.email
                            )
                        )
                ), dtoBoeking
            )
        }

    }

    fun valideerBetalingenVoorGebruiker(gebruiker: Gebruiker): List<Betaling> {
        val betalingenLijst = betalingRepository.findAllByGebruiker(gebruiker).filter { betaling: Betaling ->
            val periode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, betaling.boekingsdatum)
            periode != null && betaling.bron != null && betaling.bestemming != null && (!betaling.bron.rekeningIsGeldigInPeriode(
                periode
            ) || !betaling.bestemming.rekeningIsGeldigInPeriode(periode))
        }
        return betalingenLijst
    }
}