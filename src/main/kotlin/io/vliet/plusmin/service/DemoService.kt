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

    fun configureerDemoBetalingen(administratie: Administratie) {
        val periodes = periodeRepository.getPeriodesVoorAdministrtatie(administratie)
        val bronPeriode = periodes
            .filter { !it.periodeStartDatum.equals(it.periodeEindDatum) }
            .sortedBy { it.periodeStartDatum }[0]
        val demo = demoRepository.findByAdministratie(administratie)
        if (demo != null) {
            demoRepository.save(demo.fullCopy(bronPeriode = bronPeriode))
        } else {
            demoRepository.save(Demo(administratie = administratie, bronPeriode = bronPeriode))
        }
        periodes.filter { it.periodeStartDatum > bronPeriode.periodeStartDatum }
            .forEach { doelPeriode ->
                logger.info("Kopieer betalingen van ${bronPeriode.periodeStartDatum} naar ${doelPeriode.periodeStartDatum} voor ${administratie.naam}")
                kopieerPeriodeBetalingen(administratie, bronPeriode, doelPeriode)
            }
    }

    @Scheduled(cron = "0 1 2 * * *")
    fun nachtelijkeUpdate() {
        val parameters = demoRepository.findAll()
        parameters.forEach { demo ->
            val doelPeriode = periodeRepository.getPeriodeAdministratieEnDatum(demo.administratie.id, LocalDate.now())
                ?: throw PM_NoOpenPeriodException(listOf(demo.administratie.naam, LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)))
            kopieerPeriodeBetalingen(demo.administratie, demo.bronPeriode, doelPeriode)
        }
    }

    fun kopieerPeriodeBetalingen(administratie: Administratie, bronPeriode: Periode, doelPeriode: Periode): List<BetalingDTO> {
        val betalingen = betalingRepository.findAllByAdministratieTussenDatums(
            administratie,
            bronPeriode.periodeStartDatum,
            bronPeriode.periodeEindDatum
        ).filter { !reserveringBetalingsSoorten.contains(it.betalingsSoort) }
        val betalingenDoelPeriode = betalingen.map { betaling ->
            val boekingsDatum = shiftDatumNaarPeriodeMetZelfdeDag(betaling.boekingsdatum, doelPeriode)
            val wordtDezeMaandBetalingVerwacht =
                (betaling.bron?.maanden.isNullOrEmpty() || betaling.bron.maanden!!.contains(boekingsDatum.monthValue)) &&
                        (betaling.bestemming?.maanden.isNullOrEmpty() || betaling.bestemming.maanden!!.contains(
                            boekingsDatum.monthValue
                        ))
            val vergelijkbareBetalingen = betalingRepository.findVergelijkbareBetalingen(
                administratie,
                boekingsDatum,
                betaling.bedrag
            )
            if (boekingsDatum <= LocalDate.now() && wordtDezeMaandBetalingVerwacht && vergelijkbareBetalingen.isEmpty()) {
                val sortOrder = betalingService.berekenSortOrder(administratie, boekingsDatum)

                val nieuweBetaling = Betaling(
                    boekingsdatum = boekingsDatum,
                    bedrag = betaling.bedrag,
                    omschrijving = betaling.omschrijving,
                    sortOrder = sortOrder,
                    bron = betaling.bron,
                    bestemming = betaling.bestemming,
                    betalingsSoort = betaling.betalingsSoort,
                    administratie = administratie,
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
        } catch (_: Exception) {
            logger.warn("Datum teruggezet naar 28: $datum")
            periode.periodeStartDatum.withDayOfMonth(28)
        }
        return when {
            newDate.isBefore(periode.periodeStartDatum) -> newDate.plusMonths(1)
            newDate.isAfter(periode.periodeEindDatum) -> newDate.minusMonths(1)
            else -> newDate
        }
    }

    fun deleteBetalingenInPeriode(administratie: Administratie, periodeId: Long) {
        val periode = periodeRepository.findById(periodeId)
            .orElseGet { throw PM_PeriodeNotFoundException(listOf(periodeId.toString())) }
        if (periode.administratie.id != administratie.id) {
            throw PM_PeriodeNotFoundException(listOf(periodeId.toString()))
        }
        betalingRepository.deleteAllByAdministratieTussenDatums(
            administratie,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        )
    }
}