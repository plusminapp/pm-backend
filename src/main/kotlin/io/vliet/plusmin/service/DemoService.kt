package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class DemoService {
    @Autowired
    lateinit var demoRepository: DemoRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var betalingService: BetalingService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun configureerDemoBetalingen(gebruiker: Gebruiker) {
        val periodes = periodeRepository.getPeriodesVoorGebruiker(gebruiker)
        val bronPeriode = periodes
            .filter { it.periodeStartDatum != it.periodeEindDatum }
            .sortedBy { it.periodeStartDatum }[0]
        val demo = demoRepository.findByGebruiker(gebruiker)
        if (demo != null) {
            demoRepository.save(demo.fullCopy(bronPeriode = bronPeriode))
        } else {
            demoRepository.save(Demo(gebruiker = gebruiker, bronPeriode = bronPeriode))
        }
        periodes.filter { it.periodeStartDatum > bronPeriode.periodeStartDatum }
            .forEach { doelPeriode ->
                logger.info("Kopieer betalingen van ${bronPeriode.periodeStartDatum} naar ${doelPeriode.periodeStartDatum} voor ${gebruiker.bijnaam}")
                kopieerPeriodeBetalingen(gebruiker, bronPeriode, doelPeriode)
            }
    }

    @Scheduled(cron = "0 1 2 * * *")
    fun nachtelijkeUpdate() {
        val parameters = demoRepository.findAll()
        parameters.forEach { demo ->
            val doelPeriode = periodeRepository.getPeriodeGebruikerEnDatum(demo.gebruiker.id, LocalDate.now())
                ?: throw IllegalStateException("Geen huidige periode voor ${demo.gebruiker.bijnaam}.")
            kopieerPeriodeBetalingen(demo.gebruiker, demo.bronPeriode, doelPeriode)
        }
    }

    fun kopieerPeriodeBetalingen(gebruiker: Gebruiker, bronPeriode: Periode, doelPeriode: Periode): List<BetalingDTO> {
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(
            gebruiker,
            bronPeriode.periodeStartDatum,
            bronPeriode.periodeEindDatum
        )
        val vandaag = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val betalingenDoelPeriode = betalingen.map { betaling ->
            val boekingsdatum = shiftDatumNaarPeriodeMetZelfdeDag(betaling.boekingsdatum, doelPeriode)
            val wordtDezeMaandBetalingVerwacht =
                (betaling.bron.maanden.isNullOrEmpty() || betaling.bron.maanden!!.contains(boekingsdatum.monthValue)) &&
                        (betaling.bestemming.maanden.isNullOrEmpty() || betaling.bestemming.maanden!!.contains(boekingsdatum.monthValue));
            val betalingDTO = BetalingDTO(
                boekingsdatum = boekingsdatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
                bedrag = betaling.bedrag,
                omschrijving = betaling.omschrijving,
                sortOrder = betaling.sortOrder,
                bron = betaling.bron.naam,
                bestemming = betaling.bestemming.naam,
                betalingsSoort = betaling.betalingsSoort.toString(),
            )
            if (wordtDezeMaandBetalingVerwacht && betalingDTO.boekingsdatum <= vandaag) {
                betalingService.creeerBetaling(gebruiker, betalingDTO)
            } else {
                logger.info("Betaling op ${betalingDTO.boekingsdatum} ${if (!wordtDezeMaandBetalingVerwacht) "nog" else ""} niet gekopieerd: ${betalingDTO.omschrijving}")
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