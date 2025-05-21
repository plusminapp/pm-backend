package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.BudgetRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.Integer.parseInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
class BetalingService {
    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var budgetRepository: BudgetRepository

    @Autowired
    lateinit var rekeningService: RekeningService

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
            throw IllegalStateException("Op $boekingsDatum is er geen OPEN periode voor ${gebruiker.bijnaam}.")
        }
        val betalingList = this.findMatchingBetaling(gebruiker, betalingDTO)
        val betaling = if (betalingList.isNotEmpty()) {
            logger.info("Betaling bestaat al: ${betalingList[0].omschrijving} met id ${betalingList[0].id} voor ${gebruiker.bijnaam}")
            update(betalingList[0], betalingDTO)
        } else {
            val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, betalingDTO.bron).getOrNull()
                ?: throw IllegalStateException("${betalingDTO.bron} bestaat niet voor ${gebruiker.bijnaam}.")
            val bestemming = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, betalingDTO.bestemming).getOrNull()
                ?: throw IllegalStateException("${betalingDTO.bron} bestaat niet voor ${gebruiker.bijnaam}.")
            val budgetRekening = if (betalingDTO.betalingsSoort == Betaling.BetalingsSoort.INKOMSTEN.toString() ||
                betalingDTO.betalingsSoort == Betaling.BetalingsSoort.INKOMSTEN.toString()
            ) bron else bestemming
            val budget: Budget? =
                if (!betalingDTO.budgetNaam.isNullOrBlank()) {
                    budgetRepository.findByRekeningEnBudgetNaam(budgetRekening, betalingDTO.budgetNaam)
                        ?: run {
                            logger.warn("Budget ${betalingDTO.budgetNaam} niet gevonden bij rekening ${budgetRekening.naam} voor ${gebruiker.bijnaam}.")
                            null
                        }
                } else null
            val laatsteSortOrder: String? = betalingRepository.findLaatsteSortOrder(gebruiker, boekingsDatum)
            val sortOrderDatum = betalingDTO.boekingsdatum.replace("-", "")
            val sortOrder = if (laatsteSortOrder == null) sortOrderDatum + ".900"
            else {
                val sortOrderTeller = (parseInt(laatsteSortOrder.split(".")[1]) - 10).toString()
                sortOrderDatum + "." + sortOrderTeller
            }


            logger.info("Opslaan betaling ${betalingDTO.omschrijving} voor ${gebruiker.bijnaam}")
            Betaling(
                gebruiker = gebruiker,
                boekingsdatum = LocalDate.parse(betalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
                bedrag = betalingDTO.bedrag.toBigDecimal(),
                omschrijving = betalingDTO.omschrijving,
                betalingsSoort = Betaling.BetalingsSoort.valueOf(betalingDTO.betalingsSoort),
                bron = bron,
                bestemming = bestemming,
                sortOrder = sortOrder,
                budget = budget
            )
        }
        return betalingRepository.save(betaling).toDTO()
    }

    fun update(oldBetaling: Betaling, newBetalingDTO: BetalingDTO): Betaling {
        val gebruiker = oldBetaling.gebruiker
        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newBetalingDTO.bron).getOrNull()
            ?: oldBetaling.bron
        val bestemming = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newBetalingDTO.bestemming).getOrNull()
            ?: oldBetaling.bestemming
        val budgetRekening = if (
            newBetalingDTO.betalingsSoort.uppercase(Locale.getDefault()) == Betaling.BetalingsSoort.INKOMSTEN.toString() ||
            newBetalingDTO.betalingsSoort.uppercase(Locale.getDefault()) == Betaling.BetalingsSoort.RENTE.toString()
        ) bron else bestemming
        val budget = if (!newBetalingDTO.budgetNaam.isNullOrBlank()) {
            budgetRepository.findByRekeningEnBudgetNaam(budgetRekening, newBetalingDTO.budgetNaam)
                ?: run {
                    logger.warn("Budget ${newBetalingDTO.budgetNaam} niet gevonden bij rekening ${budgetRekening.naam} voor ${gebruiker.bijnaam}.")
                    null
                }
        } else null
        logger.info("Update betaling ${oldBetaling.id}/${newBetalingDTO.omschrijving} voor ${gebruiker.bijnaam} met budget ${budget?.id ?: (newBetalingDTO.budgetNaam + "niet gevonden")} ")
        val newBetaling = oldBetaling.fullCopy(
            boekingsdatum = LocalDate.parse(newBetalingDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
            bedrag = newBetalingDTO.bedrag.toBigDecimal(),
            omschrijving = newBetalingDTO.omschrijving,
            betalingsSoort = Betaling.BetalingsSoort.valueOf(newBetalingDTO.betalingsSoort),
            bron = bron,
            bestemming = bestemming,
            budget = budget
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


    fun valideerRekeningenVoorGebruiker(gebruiker: Gebruiker): List<Betaling> {
        val betalingenLijst = betalingRepository
            .findAllByGebruiker(gebruiker)
            .filter { betaling: Betaling ->
                val periode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, betaling.boekingsdatum)
                periode != null &&
                        (!rekeningService.rekeningIsGeldigInPeriode(betaling.bron, periode) ||
                        !rekeningService.rekeningIsGeldigInPeriode(betaling.bestemming, periode))
            }
        return betalingenLijst
    }
}