package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Reservering
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

    @Autowired
    lateinit var reserveringService: ReserveringService

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
            throw IllegalStateException("Op $boekingsDatum is er geen OPEN periode voor ${gebruiker.bijnaam}.")
        }
        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, betalingDTO.bron)
            ?: throw IllegalStateException("${betalingDTO.bron} bestaat niet voor ${gebruiker.bijnaam}.")
        val bestemming =
            rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, betalingDTO.bestemming)
                ?: throw IllegalStateException("${betalingDTO.bestemming} bestaat niet voor ${gebruiker.bijnaam}.")

        val betalingList = this.findMatchingBetaling(gebruiker, betalingDTO)

        val betaling = if (betalingList.isNotEmpty()) {
            logger.info("Betaling bestaat al: ${betalingList[0].omschrijving} met id ${betalingList[0].id} voor ${gebruiker.bijnaam}")
            update(betalingList[0], betalingDTO)
        } else {
            val sortOrder = berekenSortOrder(gebruiker, boekingsDatum)
            logger.info("Nieuwe betaling ${betalingDTO.omschrijving} voor ${gebruiker.bijnaam}")
            Betaling(
                gebruiker = gebruiker,
                boekingsdatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
                bedrag = betalingDTO.bedrag.toBigDecimal(),
                omschrijving = betalingDTO.omschrijving,
                betalingsSoort = Betaling.BetalingsSoort.valueOf(betalingDTO.betalingsSoort),
                bron = bron,
                bestemming = bestemming,
                sortOrder = sortOrder,
            )
        }

        if (betalingDTO.betalingsSoort == Betaling.BetalingsSoort.SPAREN.name ||
            betalingDTO.betalingsSoort == Betaling.BetalingsSoort.RENTE.name
        ) {
            val bufferRekeningNaam = rekeningRepository
                .findBufferRekeningVoorGebruiker(gebruiker)
                .sortedBy { it.sortOrder }[0]
                .naam
            val spaarPotje = if (betalingDTO.spaarPotje.isNullOrBlank()) {
                val spaarpotten = rekeningRepository.findSpaarpottenVoorGebruiker(gebruiker)
                if (spaarpotten.isEmpty()) {
                    throw IllegalStateException("Er is geen spaarpotje voor ${gebruiker.bijnaam}.")
                } else {
                    if (spaarpotten.size > 1) {
                        logger.warn("Er is meer dan één spaarpotje voor ${gebruiker.bijnaam}, ${spaarpotten[0].naam} wordt gebruikt.")
                    }
                    spaarpotten[0].naam
                }
            } else {
                betalingDTO.spaarPotje
            }
            val reserveringDTO = Reservering.ReserveringDTO(
                boekingsdatum = betalingDTO.boekingsdatum,
                reserveringsHorizon = null,
                bedrag = betalingDTO.bedrag,
                omschrijving = "${betalingDTO.betalingsSoort} voor ${spaarPotje}",
                bron = bufferRekeningNaam,
                bestemming = spaarPotje
            )
            reserveringService.creeerReservering(gebruiker, reserveringDTO)
        }

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
        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newBetalingDTO.bron)
            ?: oldBetaling.bron
        val bestemming =
            rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newBetalingDTO.bestemming)
                ?: oldBetaling.bestemming
        val boekingsDatum = LocalDate.parse(newBetalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val sortOrder = berekenSortOrder(gebruiker, boekingsDatum)
        logger.info("Update betaling ${oldBetaling.id}/${newBetalingDTO.omschrijving} voor ${gebruiker.bijnaam} ")
        val newBetaling = oldBetaling.fullCopy(
            boekingsdatum = boekingsDatum,
            bedrag = newBetalingDTO.bedrag.toBigDecimal(),
            omschrijving = newBetalingDTO.omschrijving,
            betalingsSoort = Betaling.BetalingsSoort.valueOf(newBetalingDTO.betalingsSoort),
            bron = bron,
            bestemming = bestemming,
            sortOrder = sortOrder,
        )
        return betalingRepository.save(newBetaling)
    }

    fun findMatchingBetaling(gebruiker: Gebruiker, betalingDTO: BetalingDTO): List<Betaling> {
        return betalingRepository.findMatchingBetaling(
            gebruiker = gebruiker,
            boekingsdatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
            bedrag = betalingDTO.bedrag.toBigDecimal(),
            omschrijving = betalingDTO.omschrijving,
            betalingsSoort = Betaling.BetalingsSoort.valueOf(betalingDTO.betalingsSoort),
        )
    }


    fun valideerBetalingenVoorGebruiker(gebruiker: Gebruiker): List<Betaling> {
        val betalingenLijst = betalingRepository
            .findAllByGebruiker(gebruiker)
            .filter { betaling: Betaling ->
                val periode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, betaling.boekingsdatum)
                periode != null &&
                        (!betaling.bron.rekeningIsGeldigInPeriode(periode) ||
                                !betaling.bestemming.rekeningIsGeldigInPeriode(periode))
            }
        return betalingenLijst
    }
}