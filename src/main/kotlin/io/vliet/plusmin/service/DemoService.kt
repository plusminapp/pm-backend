package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.lang.Integer.parseInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class DemoService {
    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var betalingService: BetalingService

    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Scheduled(cron = "0 1 2 * * *")
//    @Scheduled(cron = "0 * * * * *")
    fun nachtelijkeUpdate() {
        val parameters = listOf(
            Pair(54933L, 54935L), // Bernhard
            Pair(54936L, 54938L), // Ursula
            Pair(54939L, 54942L), // Alex
            Pair(54945L, 54947L), // Abel
        )
        parameters.forEach { (gebruikerId, bronPeriodeId) ->
            val gebruiker = gebruikerRepository.findById(gebruikerId)
                .orElseGet { throw IllegalStateException("Gebruiker $gebruikerId bestaat niet.") }
            logger.info("Nachtelijke update voor ${gebruiker.bijnaam} van periode $bronPeriodeId.")
            val bronPeriode = periodeRepository.findById(bronPeriodeId)
                .orElseGet { throw IllegalStateException("Periode $bronPeriodeId bestaat niet.") }
            val doelPeriode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, LocalDate.now())
                ?: throw IllegalStateException("Geen huidige periode voor ${gebruiker.bijnaam}.")
            kopieerPeriodeBetalingen(gebruiker, bronPeriode.id, doelPeriode.id)
        }
    }

    fun kopieerPeriodeBetalingen(gebruiker: Gebruiker, bronPeriodeId: Long, doelPeriodeId: Long): List<BetalingDTO> {
        val bronPeriode = periodeRepository.findById(bronPeriodeId)
            .orElseGet { throw IllegalStateException("Periode $bronPeriodeId bestaat niet.") }
        val doelPeriode = periodeRepository.findById(doelPeriodeId)
            .orElseGet { throw IllegalStateException("Periode $doelPeriodeId bestaat niet.") }
        if (bronPeriode.gebruiker.id != gebruiker.id || doelPeriode.gebruiker.id != gebruiker.id) {
            throw IllegalStateException("Periode $bronPeriodeId of $doelPeriodeId is niet van ${gebruiker.bijnaam}.")
        }
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(
            gebruiker,
            bronPeriode.periodeStartDatum,
            bronPeriode.periodeEindDatum
        )
        val vandaag = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val betalingenDoelPeriode = betalingen.map { betaling ->
            val betalingDTO = BetalingDTO(
                boekingsdatum = shiftDatumNaarPeriodeMetZelfdeDag(betaling.boekingsdatum, doelPeriode).format(
                    DateTimeFormatter.ISO_LOCAL_DATE
                ),
                bedrag = betaling.bedrag.toString(),
                omschrijving = betaling.omschrijving,
                sortOrder = betaling.sortOrder,
                bron = betaling.bron.naam,
                bestemming = betaling.bestemming.naam,
                betalingsSoort = betaling.betalingsSoort.toString(),
                budgetNaam = betaling.budget?.budgetNaam
            )
            if (betalingDTO.boekingsdatum <= vandaag) {
                betalingService.creeerBetaling(gebruiker, betalingDTO)
            } else {
                logger.info("Betaling op ${betalingDTO.boekingsdatum} nog niet gekopieerd: ${betalingDTO.omschrijving}")
                betalingDTO
            }
        }
        return betalingenDoelPeriode
    }

    fun shiftDatumNaarPeriodeMetZelfdeDag(datum: LocalDate, periode: Periode): LocalDate {
        val dayOfMonth = datum.dayOfMonth
        val newDate = try {
            periode.periodeStartDatum.withDayOfMonth(dayOfMonth)
        } catch (e: Exception) {
            logger.warn("Datum teruggezet naar 28: $datum")
            periode.periodeStartDatum.withDayOfMonth(28)
        }
        return when {
            newDate.isBefore(periode.periodeStartDatum) -> newDate.plusMonths(1)
            newDate.isAfter(periode.periodeEindDatum) -> newDate.minusMonths(1)
            else -> newDate
        }
    }

    fun deleteBetalingenInPeriode(gebruiker: Gebruiker, periodeId: Long) {
        val periode = periodeRepository.findById(periodeId)
            .orElseGet { throw IllegalStateException("Periode $periodeId bestaat niet.") }
        if (periode.gebruiker.id != gebruiker.id) {
            throw IllegalStateException("Periode $periodeId is niet van ${gebruiker.bijnaam}.")
        }
        betalingRepository.deleteAllByGebruikerTussenDatums(
            gebruiker,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        )
    }
}