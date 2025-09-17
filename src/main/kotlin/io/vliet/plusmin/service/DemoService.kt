package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.domain.Betaling.Companion.reserveringBetalingsSoorten
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

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
            .filter { !it.periodeStartDatum.equals(it.periodeEindDatum) }
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
        ).filter { !reserveringBetalingsSoorten.contains(it.betalingsSoort) }
        val betalingenDoelPeriode = betalingen.map { betaling ->
            val boekingsDatum = shiftDatumNaarPeriodeMetZelfdeDag(betaling.boekingsdatum, doelPeriode)
            val wordtDezeMaandBetalingVerwacht =
                (betaling.bron?.maanden.isNullOrEmpty() || betaling.bron.maanden!!.contains(boekingsDatum.monthValue)) &&
                        (betaling.bestemming?.maanden.isNullOrEmpty() || betaling.bestemming.maanden!!.contains(
                            boekingsDatum.monthValue
                        ));
            if (wordtDezeMaandBetalingVerwacht && boekingsDatum <= LocalDate.now()) {
                val sortOrder = betalingService.berekenSortOrder(gebruiker, boekingsDatum)

                val reservering = betalingRepository.findByGebruikerOpDatumBronBestemming(
                    gebruiker,
                    datum = doelPeriode.periodeStartDatum,
                    reserveringBron = TODO(),
                    reserveringBestemming = TODO(),
                )
                val nieuweBetaling = Betaling(
                    boekingsdatum = boekingsDatum,
                    bedrag = betaling.bedrag,
                    omschrijving = betaling.omschrijving,
                    sortOrder = sortOrder,
                    bron = betaling.bron,
                    bestemming = betaling.bestemming,
                    betalingsSoort = betaling.betalingsSoort,
                    gebruiker = gebruiker,
                    reserveringsHorizon = betaling.reserveringsHorizon,
                    reserveringBron = betaling.reserveringBron,
                    reserveringBestemming = betaling.reserveringBestemming
                )
                logger.info("Betaling op ${nieuweBetaling.boekingsdatum}  gekopieerd: ${nieuweBetaling.omschrijving}")
                betalingRepository.save(nieuweBetaling).toDTO()
            } else {
                logger.info("Betaling op ${boekingsDatum} ${if (!wordtDezeMaandBetalingVerwacht) "nog" else ""} niet gekopieerd: ${betaling.omschrijving}")
                betaling.toDTO()
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