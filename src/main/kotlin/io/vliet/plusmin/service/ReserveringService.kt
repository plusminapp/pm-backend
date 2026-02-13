package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMiddelenRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.isPotjeVoorNu
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.Integer.parseInt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ReserveringService {
    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningUtilitiesService: RekeningUtilitiesService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun creeerAlleReserveringen(administratie: Administratie) {
        var reserverenMogelijk = true
        val huidigePeriode = periodeRepository.getLaatstePeriodeVoorAdministratie(administratie.id)
        var reserveringHorizon: LocalDate? =
            betalingRepository.getReserveringsHorizon(administratie) ?: huidigePeriode?.periodeStartDatum
        while (reserverenMogelijk &&
            reserveringHorizon != null &&
            huidigePeriode != null &&
            reserveringHorizon <= huidigePeriode.periodeEindDatum
        ) {
            reserverenMogelijk = creeerReserveringen(administratie)
            reserveringHorizon = betalingRepository.getReserveringsHorizon(administratie)
            logger.info("Reserveringen aangemaakt tot en met $reserveringHorizon voor administratie ${administratie.naam}.")
        }
    }

    /*
    * Creëert reserveringen voor rekeningen van het type 'potje voor nu' van de vorige reserveringsdatum + 1 tot de volgende inkomsten.
    *    vorigeReserveringsDatum = vorige reserveringsDatum
    *    bepaal de saldi op de vorigeReserveringsDatum & corrigeer die saldi
    *    volgendeInkomstenDatum = de volgende (verwachte) inkomsten datum na de vorige reserveringsDatum
    *    vasteLasten = bereken de som(vaste lasten) van vorigeReserveringsDatum tot volgendeInkomstenDatum
    *    aantalDagen = bereken aan dagen van vorigeReserveringsDatum t/m volgendeInkomstenDatum
    *    leefgeldPerDag = bereken leefgeld per dag
    *    leefgeld = aantalDagen x leefgeldPerDag
    *    ALS leefgeld + vasteLasten > saldo(bufferIN) DAN stop
    *    ANDERS vul alle potjes en herhaal
     */
    fun creeerReserveringen(administratie: Administratie): Boolean {
        val vorigeReserveringsDatum =
            betalingRepository.getReserveringsHorizon(administratie)
                ?: periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(administratie)?.periodeEindDatum
                ?: throw PM_LaatsteGeslotenPeriodeNotFoundException(listOf(administratie.naam))
        val volgendeInkomstenDatum =
            rekeningUtilitiesService.bepaalVolgendeInkomstenBetaalDagVoorAdministratie(
                administratie,
                vorigeReserveringsDatum
            )

        val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        val reserveringBufferRekening = rekeningRepository.findBufferRekeningVoorAdministratie(administratie)
            ?: throw PM_BufferRekeningNotFoundException(listOf(administratie.naam))
        val saldiOpDatum =
            corigeerStartSaldi(
                vorigeReserveringsDatum.plusDays(1),
                administratie,
                dateTimeFormatter,
                reserveringBufferRekening
            )

        val vasteLastenRekeningen =
            findVasteLastenRekeningen(administratie, vorigeReserveringsDatum, volgendeInkomstenDatum)
        val leefgeldPerDag = leefgeldPerDag(administratie, vorigeReserveringsDatum)

        val aantalDagenTotInkomen =
            java.time.temporal.ChronoUnit.DAYS.between(vorigeReserveringsDatum.plusDays(1), volgendeInkomstenDatum)
                .toInt()
        val vasteLastenTotaal = vasteLastenRekeningen.sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
        val leefgeldTotaal = leefgeldPerDag.values.sumOf { it.multiply(BigDecimal(aantalDagenTotInkomen)) }
        val saldoBufferIn = saldiOpDatum
            .find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
            ?.let { it.openingsReserveSaldo + it.periodeReservering } ?: BigDecimal.ZERO
        logger.debug(
            "(On)voldoende buffer saldo bij het aanmaken van reserveringen voor periode vanaf " +
                    "${vorigeReserveringsDatum.plusDays(1)} " +
                    "voor ${administratie.naam}: " +
                    "beschikbaar saldo bufferIn is $saldoBufferIn, " +
                    "terwijl vaste lasten $vasteLastenTotaal en leefgeld $leefgeldTotaal bedraagt."
        )
        if (vasteLastenTotaal + leefgeldTotaal > saldoBufferIn) {
            // TODO licht de frontend in
            throw PM_OnvoldoendeBufferSaldoException(
                listOf(
                    saldoBufferIn.toString(),
                    vorigeReserveringsDatum.plusDays(1).toString(),
                    administratie.naam,
                    (vasteLastenTotaal + leefgeldTotaal).toString(),
                )
            )
        }
        // TODO wat als er op de laatste dag van een periode inkomsten zijn en dus de vorigeReservering +1 in de nieuwe periode ligt?
        val fakePeriode = periodeService.getFakePeriode(administratie, vorigeReserveringsDatum.plusDays(1))

        val volgendeReserveringsDatum = minOf(volgendeInkomstenDatum, fakePeriode.periodeEindDatum)
        val aantalDagenTotVolgendeReservering =
            java.time.temporal.ChronoUnit.DAYS.between(
                vorigeReserveringsDatum.plusDays(1),
                volgendeReserveringsDatum.plusDays(1)
            ).toInt()
        val boekingsDatum = fakePeriode.periodeStartDatum
        vasteLastenRekeningen.forEach {
            val betaalDatum =
                if (vorigeReserveringsDatum.dayOfMonth < it.budgetBetaalDag!!) {
                    val laatsteDagVanMaand = vorigeReserveringsDatum.lengthOfMonth()
                    vorigeReserveringsDatum.withDayOfMonth(minOf(it.budgetBetaalDag, laatsteDagVanMaand))
                } else {
                    val laatsteDagVanMaand = vorigeReserveringsDatum.plusMonths(1).lengthOfMonth()
                    vorigeReserveringsDatum.plusMonths(1).withDayOfMonth(minOf(it.budgetBetaalDag, laatsteDagVanMaand))
                }
            logger.info(
                "Creëer reservering voor vaste last ${it.naam} op $betaalDatum voor administratie ${administratie.naam}. betaalDatum <= fakePeriode.periodeEindDatum: ${betaalDatum <= fakePeriode.periodeEindDatum}, it.maanden=${it.maanden}, it.maanden?.contains(betaalDatum.monthValue)=${
                    it.maanden?.contains(
                        betaalDatum.monthValue
                    )
                }"
            )
            if (betaalDatum <= fakePeriode.periodeEindDatum &&
                (it.maanden.isNullOrEmpty() || it.maanden?.contains(betaalDatum.monthValue) ?: true)
            ) {
                betalingRepository.save(
                    Betaling(
                        administratie = administratie,
                        boekingsdatum = boekingsDatum,
                        reserveringsHorizon = volgendeReserveringsDatum,
                        bedrag = it.budgetBedrag ?: BigDecimal.ZERO,
                        omschrijving = "Reservering voor vaste last ${it.naam}",
                        reserveringBron = reserveringBufferRekening,
                        reserveringBestemming = it,
                        sortOrder = berekenSortOrder(administratie, boekingsDatum),
                        betalingsSoort = Betaling.BetalingsSoort.RESERVEREN,
                    )
                )
            }
        }

        leefgeldPerDag.forEach { (rekening, bedragPerDag) ->
            val totaalLeefgeld = bedragPerDag.multiply(BigDecimal(aantalDagenTotVolgendeReservering))
            if (bedragPerDag.compareTo(BigDecimal.ZERO) != 0)
                betalingRepository.save(
                    Betaling(
                        administratie = administratie,
                        boekingsdatum = boekingsDatum,
                        reserveringsHorizon = volgendeReserveringsDatum,
                        bedrag = totaalLeefgeld,
                        omschrijving = "Reservering voor leefgeld op ${rekening.naam}",
                        reserveringBron = reserveringBufferRekening,
                        reserveringBestemming = rekening,
                        sortOrder = berekenSortOrder(administratie, boekingsDatum),
                        betalingsSoort = Betaling.BetalingsSoort.RESERVEREN,
                    )
                )
        }
        return true
    }

    private fun findVasteLastenRekeningen(
        administratie: Administratie,
        vorigeReserveringsDatum: LocalDate,
        volgendeInkomstenDatum: LocalDate
    ): List<Rekening> {
        val vasteLastenTotVolgendeInkomstenDatum = rekeningRepository
            .findRekeningenVoorAdministratie(administratie)
            .filter { it.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST }
            .filter { rekening ->
                val rekeningBetaalDag = rekening.budgetBetaalDag ?: return@filter false
                val volgendeRekeningBetaaldatum =
                    if (vorigeReserveringsDatum.dayOfMonth < rekeningBetaalDag) {
                        val laatsteDagVanMaand = vorigeReserveringsDatum.lengthOfMonth()
                        vorigeReserveringsDatum.withDayOfMonth(minOf(laatsteDagVanMaand, rekeningBetaalDag))
                    } else {
                        val laatsteDagVanMaand = vorigeReserveringsDatum.plusMonths(1).lengthOfMonth()
                        vorigeReserveringsDatum.plusMonths(1)
                            .withDayOfMonth(minOf(laatsteDagVanMaand, rekeningBetaalDag))
                    }
                logger.debug(
                    "findVasteLastenRekeningen: rekening=${rekening.naam}, vorigeReserveringsDatum=$vorigeReserveringsDatum, volgendeBetaalDatum=$volgendeRekeningBetaaldatum, volgendeInkomstenDatum=$volgendeInkomstenDatum, ${
                        !volgendeRekeningBetaaldatum.isAfter(
                            volgendeInkomstenDatum
                        )
                    }"
                )
                !volgendeRekeningBetaaldatum.isAfter(volgendeInkomstenDatum)
            }
        return vasteLastenTotVolgendeInkomstenDatum
    }

    fun leefgeldPerDag(
        administratie: Administratie,
        vorigeReserveringsDatum: LocalDate,
    ): Map<Rekening, BigDecimal> {
        val leefgeldRekeningen = rekeningRepository
            .findRekeningenVoorAdministratie(administratie)
            .filter { it.rekeningGroep.budgetType == RekeningGroep.BudgetType.CONTINU }
        val aantalDagenInDeMaand = vorigeReserveringsDatum.lengthOfMonth()
        return leefgeldRekeningen
            .associateWith {
                logger.debug("leefgeldPerDag: rekening=${it.naam}, aantalDagenInDeMaand=$aantalDagenInDeMaand, budgetBedrag=${it.budgetBedrag}, budgetPeriodiciteit=${it.budgetPeriodiciteit}")
                (if (it.budgetPeriodiciteit == Rekening.BudgetPeriodiciteit.MAAND)
                    it.budgetBedrag?.divide(BigDecimal(aantalDagenInDeMaand), 2, java.math.RoundingMode.HALF_UP)
                else it.budgetBedrag?.divide(BigDecimal(7), 2, java.math.RoundingMode.HALF_UP)) ?: BigDecimal.ZERO
            }
    }

    fun corigeerStartSaldi(
        vorigeReserveringsDatum: LocalDate,
        administratie: Administratie,
        dateTimeFormatter: DateTimeFormatter?,
        reserveringBufferRekening: Rekening
    ): List<Saldo> {
        val saldiOpPeildatum: List<Saldo> =
            standInPeriodeService.berekenSaldiOpDatum(administratie, vorigeReserveringsDatum)
        val initieleBuffer =
            saldiOpPeildatum
                .find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
                ?.let { it.openingsReserveSaldo + it.periodeReservering }
                ?: BigDecimal.ZERO
        val initieleReserveringTekorten =
            saldiOpPeildatum.filter { potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
                .sumOf { if (it.openingsReserveSaldo < BigDecimal.ZERO) it.openingsReserveSaldo else BigDecimal.ZERO }
        val initieleReserveringOverschot =
            saldiOpPeildatum.filter { potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.rekening.budgetAanvulling == Rekening.BudgetAanvulling.TOT }
                .sumOf { if (it.openingsReserveSaldo > BigDecimal.ZERO) it.openingsReserveSaldo else BigDecimal.ZERO }
        logger.debug("Initiele buffer op ${vorigeReserveringsDatum} voor ${administratie.naam} is $initieleBuffer, reserveringstekorten $initieleReserveringTekorten, delta ${initieleBuffer + initieleReserveringTekorten}.")
        if (initieleBuffer + initieleReserveringTekorten + initieleReserveringOverschot < BigDecimal.ZERO) throw PM_OnvoldoendeBufferSaldoException(
            listOf(
                initieleBuffer.toString(),
                vorigeReserveringsDatum.toString(),
                administratie.naam,
                initieleReserveringTekorten.toString(),
            )
        )
        val startSaldiVanPeriode =
            if ((initieleReserveringTekorten == BigDecimal.ZERO && initieleReserveringOverschot == BigDecimal.ZERO) ||
                vorigeReserveringsDatum > (administratie.vandaag ?: LocalDate.now())
            ) saldiOpPeildatum
            else {
                saldiOpPeildatum.map {
                    if (potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.openingsReserveSaldo < BigDecimal.ZERO) {
                        betalingRepository.save(
                            Betaling(
                                administratie = administratie,
                                boekingsdatum = vorigeReserveringsDatum.minusDays(1),
                                reserveringsHorizon = vorigeReserveringsDatum.minusDays(1),
                                bedrag = -it.openingsReserveSaldo,
                                omschrijving =
                                    "Correctie voor tekort van ${it.rekening.naam} in periode ${
                                        vorigeReserveringsDatum.format(
                                            dateTimeFormatter
                                        )
                                    }",
                                reserveringBron = reserveringBufferRekening,
                                reserveringBestemming = it.rekening,
                                sortOrder = berekenSortOrder(administratie, vorigeReserveringsDatum.minusDays(1)),
                                betalingsSoort = Betaling.BetalingsSoort.RESERVEREN,
                            )
                        )
                        logger.warn("Buffer reserveringstekort van ${-it.openingsReserveSaldo} voor ${it.rekening.naam} bij start van periode ${vorigeReserveringsDatum} voor ${administratie.naam} aangevuld vanuit buffer.")
                        it.fullCopy(openingsReserveSaldo = BigDecimal.ZERO)
                    } else if (potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.rekening.budgetAanvulling == Rekening.BudgetAanvulling.TOT && it.openingsReserveSaldo > BigDecimal.ZERO) {
                        betalingRepository.save(
                            Betaling(
                                administratie = administratie,
                                boekingsdatum = vorigeReserveringsDatum.minusDays(1),
                                reserveringsHorizon = vorigeReserveringsDatum.minusDays(1),
                                bedrag = it.openingsReserveSaldo,
                                omschrijving =
                                    "Correctie voor overschot van ${it.rekening.naam} in periode ${
                                        vorigeReserveringsDatum.format(
                                            dateTimeFormatter
                                        )
                                    }",
                                reserveringBron = it.rekening,
                                reserveringBestemming = reserveringBufferRekening,
                                sortOrder = berekenSortOrder(administratie, vorigeReserveringsDatum.minusDays(1)),
                                betalingsSoort = Betaling.BetalingsSoort.RESERVEREN,
                            )
                        )
                        logger.warn("Buffer reserveringsoverschot van ${it.openingsReserveSaldo} voor ${it.rekening.naam} bij start van periode ${vorigeReserveringsDatum} voor ${administratie.naam} overgeheveld naar de buffer.")
                        it.fullCopy(openingsReserveSaldo = BigDecimal.ZERO)
                    } else if (it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER) {
                        it.fullCopy(openingsReserveSaldo = it.openingsReserveSaldo + initieleReserveringTekorten + initieleReserveringOverschot)
                    } else it
                }
            }
        return startSaldiVanPeriode
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

    fun updateOpeningsReserveringsSaldo(administratie: Administratie) {
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(administratie)
        val basisPeriodeSaldi = saldoRepository.findAllByPeriode(basisPeriode)

        val saldoBetaalMiddelen = basisPeriodeSaldi
            .filter { betaalMiddelenRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.openingsBalansSaldo + it.correctieBoeking }
        val saldoPotjesVoorNu = basisPeriodeSaldi
            .filter { it.rekening.rekeningGroep.isPotjeVoorNu() }
            .sumOf { it.openingsReserveSaldo }
        logger.info(
            "Openings saldo betaalmiddelen: $saldoBetaalMiddelen, " +
                    "openings saldo potjes voor nu: $saldoPotjesVoorNu, " +
                    "totaal: ${saldoBetaalMiddelen - saldoPotjesVoorNu}"
        )
        val bufferReserveSaldo = basisPeriodeSaldi
            .find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
            ?: throw PM_GeenBufferVoorSaldoException(listOf(basisPeriode.id.toString(), administratie.naam))
        saldoRepository.save(
            bufferReserveSaldo.fullCopy(
                openingsReserveSaldo = saldoBetaalMiddelen - saldoPotjesVoorNu
            )
        )
    }

}