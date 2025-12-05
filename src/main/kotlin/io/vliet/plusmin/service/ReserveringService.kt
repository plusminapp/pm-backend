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
    lateinit var standStartVanPeriodeService: StandStartVanPeriodeService

    @Autowired
    lateinit var standMutatiesTussenDatumsService: StandMutatiesTussenDatumsService

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var cashflowService: CashflowService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun creeerReserveringen(administratie: Administratie) {
        val periodes = periodeRepository
            .getPeriodesVoorAdministrtatie(administratie)
            .sortedBy { it.periodeStartDatum }
            .drop(1)
        periodes.forEach { periode ->
            creeerReserveringenVoorPeriode(administratie, periode.periodeStartDatum)
        }
    }

    /*
    * Creëert reserveringen voor alle rekeningen van het type 'potje voor nu' voor de gegeven periode.
    *    vorigeReserveringsDatum = vorige reserveringsDatum
    *    volgendeInkomstenDatum = de volgende inkomsten datum
    *    ALS volgendeInkomstenDatum <= vorigeReserveringsDatum DAN return
    *    bepaal de saldi op de peildatum
    *    corrigeer de saldi op de peildatum
    *    vasteLasten = bereken de som(vaste lasten) tot volgendeInkomstenDatum
    *    aantalDagen = bereken aan dagen t/m volgendeInkomstenDatum
    *    leefgeldPerDag = bereken leefgeld per dag
    *    leefgeld = aantalDagen x leefgeldPerDag
    *    ALS leefgeld + vasteLasten > saldo(bufferIN) DAN sla alarm
    *    ANDERS vul alle potjes
     */
    fun creeerReserveringenVoorPeriode(administratie: Administratie, peilDatum: LocalDate) {
        val vorigeReserveringsDatum =
            betalingRepository.getReserveringsHorizon(administratie)
        val volgendeBetaalDatum =
            rekeningUtilitiesService.bepaalVolgendeInkomstenBetaalDagVoorAdministratie(administratie, peilDatum)
        if (volgendeBetaalDatum.isBefore(vorigeReserveringsDatum)) {
            logger.info(
                "Geen nieuwe reserveringen nodig voor periode vanaf $peilDatum voor ${administratie.naam}, " +
                        "volgende betaal datum $volgendeBetaalDatum is voor vorige reserverings datum $vorigeReserveringsDatum"
            )
            return
        }
        val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        val reserveringBufferRekening = rekeningRepository.findBufferRekeningVoorAdministratie(administratie)
            ?: throw PM_BufferRekeningNotFoundException(listOf(administratie.naam))
        val saldiOpPeilDatum =
            corigeerStartSaldi(peilDatum, administratie, dateTimeFormatter, reserveringBufferRekening)

        val vasteLastenRekeningen = findVasteLastenRekeningen(administratie, peilDatum, volgendeBetaalDatum)
        val leefgeldPerDag = leefgeldPerDag(administratie, peilDatum)

        val aantalDagen = java.time.temporal.ChronoUnit.DAYS.between(peilDatum, volgendeBetaalDatum).toInt()
        val vasteLastenTotaal = vasteLastenRekeningen.sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
        val leefgeldTotaal = leefgeldPerDag.values.sumOf { it.multiply(BigDecimal(aantalDagen)) }
        val saldoBufferIn = saldiOpPeilDatum
            .find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
            ?.let { it.openingsReserveSaldo + it.periodeReservering } ?: BigDecimal.ZERO
        if (vasteLastenTotaal + leefgeldTotaal > saldoBufferIn) {
            logger.error(
                "Onvoldoende buffer saldo bij het aanmaken van reserveringen voor periode vanaf $peilDatum voor ${administratie.naam}: " +
                        "beschikbaar saldo bufferIn is $saldoBufferIn, " +
                        "terwijl vaste lasten $vasteLastenTotaal en leefgeld $leefgeldTotaal bedraagt."
            )
            throw PM_OnvoldoendeBufferSaldoException(
                listOf(
                    saldoBufferIn.toString(),
                    peilDatum.toString(),
                    administratie.naam,
                    (vasteLastenTotaal + leefgeldTotaal).toString(),
                )
            )
        }
        vasteLastenRekeningen.forEach { betalingRepository.save(Betaling(
            administratie = administratie,
            boekingsdatum = peilDatum,
            reserveringsHorizon = volgendeBetaalDatum,
            bedrag = it.budgetBedrag ?: BigDecimal.ZERO,
            omschrijving = "Reservering voor vaste last ${it.naam}",
            reserveringBron = reserveringBufferRekening,
            reserveringBestemming = it,
            sortOrder = berekenSortOrder(administratie, peilDatum),
            betalingsSoort = Betaling.BetalingsSoort.P2P,
        )) }
        leefgeldPerDag.forEach { (rekening, bedragPerDag) ->
            val totaalLeefgeld = bedragPerDag.multiply(BigDecimal(aantalDagen))
            if (totaalLeefgeld > BigDecimal.ZERO) {
                betalingRepository.save(Betaling(
                    administratie = administratie,
                    boekingsdatum = peilDatum,
                    reserveringsHorizon = volgendeBetaalDatum,
                    bedrag = totaalLeefgeld,
                    omschrijving = "Reservering voor leefgeld op ${rekening.naam}",
                    reserveringBron = reserveringBufferRekening,
                    reserveringBestemming = rekening,
                    sortOrder = berekenSortOrder(administratie, peilDatum),
                    betalingsSoort = Betaling.BetalingsSoort.P2P,
                ))
            }
        }
    }

    private fun findVasteLastenRekeningen(
        administratie: Administratie,
        peilDatum: LocalDate,
        volgendeInkomstenDatum: LocalDate
    ): List<Rekening> {
        val vasteLastenTotVolgendeInkomstenDatum = rekeningRepository
            .findRekeningenVoorAdministratie(administratie)
            .filter { it.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST }
            .filter { rekening ->
                val rekeningBetaalDag = rekening.budgetBetaalDag ?: return@filter false
                val volgendeBetaalMaand =
                    if (peilDatum.dayOfMonth <= rekeningBetaalDag) peilDatum.monthValue
                    else peilDatum.plusMonths(1).monthValue
                val betaalDatum = LocalDate.of(
                    peilDatum.year, volgendeBetaalMaand, rekeningBetaalDag
                )
                logger.info(
                    "findVasteLastenRekeningen: rekening=${rekening.naam}, volgendeBetaalDatum=$betaalDatum, volgendeInkomstenDatum=$volgendeInkomstenDatum, ${
                        !betaalDatum.isAfter(
                            volgendeInkomstenDatum
                        )
                    }"
                )
                !betaalDatum.isAfter(volgendeInkomstenDatum)
            }
        return vasteLastenTotVolgendeInkomstenDatum
    }

    fun leefgeldPerDag(
        administratie: Administratie,
        peilDatum: LocalDate,
    ): Map<Rekening, BigDecimal> {
        val leefgeldRekeningen = rekeningRepository
            .findRekeningenVoorAdministratie(administratie)
            .filter { it.rekeningGroep.budgetType == RekeningGroep.BudgetType.CONTINU }
        val aantalDagenInDeMaand = peilDatum.lengthOfMonth()
        return leefgeldRekeningen
            .associateWith {
                (if (it.budgetPeriodiciteit == Rekening.BudgetPeriodiciteit.MAAND)
                    it.budgetBedrag?.divide(BigDecimal(aantalDagenInDeMaand))
                else it.budgetBedrag?.divide(BigDecimal(7))) ?: BigDecimal.ZERO
            }
    }

    fun corigeerStartSaldi(
        peilDatum: LocalDate,
        administratie: Administratie,
        dateTimeFormatter: DateTimeFormatter?,
        reserveringBufferRekening: Rekening
    ): List<Saldo> {
        val initieleStartSaldiOpPeildatum: List<Saldo> =
            standInPeriodeService.berekenSaldiOpDatum(administratie, peilDatum)
        val initieleBuffer =
            initieleStartSaldiOpPeildatum.find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }?.openingsReserveSaldo
                ?: BigDecimal.ZERO
        val initieleReserveringTekorten =
            initieleStartSaldiOpPeildatum.filter { potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
                .sumOf { if (it.openingsReserveSaldo < BigDecimal.ZERO) it.openingsReserveSaldo else BigDecimal.ZERO }
        val initieleReserveringOverschot =
            initieleStartSaldiOpPeildatum.filter { potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.rekening.budgetAanvulling == Rekening.BudgetAanvulling.TOT }
                .sumOf { if (it.openingsReserveSaldo > BigDecimal.ZERO) it.openingsReserveSaldo else BigDecimal.ZERO }
        logger.info("Initiele buffer bij start van periode ${peilDatum} voor ${administratie.naam} is $initieleBuffer, reserveringstekorten $initieleReserveringTekorten, delta ${initieleBuffer + initieleReserveringTekorten}.")
        if (initieleBuffer + initieleReserveringTekorten + initieleReserveringOverschot < BigDecimal.ZERO) throw PM_OnvoldoendeBufferSaldoException(
            listOf(
                initieleBuffer.toString(),
                peilDatum.toString(),
                administratie.naam,
                initieleReserveringTekorten.toString(),
            )
        )
        val startSaldiVanPeriode =
            if (initieleReserveringTekorten == BigDecimal.ZERO && initieleReserveringOverschot == BigDecimal.ZERO) initieleStartSaldiOpPeildatum
            else {
                initieleStartSaldiOpPeildatum.map {
                    if (potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.openingsReserveSaldo < BigDecimal.ZERO) {
                        betalingRepository.save(
                            Betaling(
                                administratie = administratie,
                                boekingsdatum = peilDatum.minusDays(1),
                                reserveringsHorizon = peilDatum.minusDays(1),
                                bedrag = -it.openingsReserveSaldo,
                                omschrijving =
                                    "Correctie voor tekort van ${it.rekening.naam} in periode ${
                                        peilDatum.format(
                                            dateTimeFormatter
                                        )
                                    }",
                                reserveringBron = reserveringBufferRekening,
                                reserveringBestemming = it.rekening,
                                sortOrder = berekenSortOrder(administratie, peilDatum.minusDays(1)),
                                betalingsSoort = Betaling.BetalingsSoort.P2P,
                            )
                        )
                        logger.warn("Buffer reserveringstekort van ${-it.openingsReserveSaldo} voor ${it.rekening.naam} bij start van periode ${peilDatum} voor ${administratie.naam} aangevuld vanuit buffer.")
                        it.fullCopy(openingsReserveSaldo = BigDecimal.ZERO)
                    } else if (potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.rekening.budgetAanvulling == Rekening.BudgetAanvulling.TOT && it.openingsReserveSaldo > BigDecimal.ZERO) {
                        betalingRepository.save(
                            Betaling(
                                administratie = administratie,
                                boekingsdatum = peilDatum.minusDays(1),
                                reserveringsHorizon = peilDatum.minusDays(1),
                                bedrag = it.openingsReserveSaldo,
                                omschrijving =
                                    "Correctie voor overschot van ${it.rekening.naam} in periode ${
                                        peilDatum.format(
                                            dateTimeFormatter
                                        )
                                    }",
                                reserveringBron = it.rekening,
                                reserveringBestemming = reserveringBufferRekening,
                                sortOrder = berekenSortOrder(administratie, peilDatum.minusDays(1)),
                                betalingsSoort = Betaling.BetalingsSoort.P2P,
                            )
                        )
                        logger.warn("Buffer reserveringsoverschot van ${it.openingsReserveSaldo} voor ${it.rekening.naam} bij start van periode ${peilDatum} voor ${administratie.naam} overgeheveld naar de buffer.")
                        it.fullCopy(openingsReserveSaldo = BigDecimal.ZERO)
                    } else if (it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER) {
                        it.fullCopy(openingsReserveSaldo = it.openingsReserveSaldo + initieleReserveringTekorten + initieleReserveringOverschot)
                    } else it
                }
            }
        return listOf()
    }

    fun creeerReserveringVoorVasteLast(
        rekening: Rekening,
        bedrag: BigDecimal,
        datum: LocalDate,
        reserveringBufferRekening: Rekening
    ) {
        betalingRepository.save(
            Betaling(
                administratie = rekening.rekeningGroep.administratie,
                boekingsdatum = datum,
                reserveringsHorizon = datum,
                bedrag = bedrag,
                omschrijving = "Reservering voor vaste last ${rekening.naam}",
                reserveringBron = reserveringBufferRekening,
                reserveringBestemming = rekening,
                sortOrder = berekenSortOrder(rekening.rekeningGroep.administratie, LocalDate.now()),
                betalingsSoort = Betaling.BetalingsSoort.P2P,
            )
        )
    }

    private fun creeerReserveringVoorRekening(
        mutatiesInPeilPeriode: List<Saldo>,
        rekening: Rekening,
        periode: Periode,
        budgetHorizon: LocalDate,
        administratie: Administratie,
        reserveringBufferRekening: Rekening,
        dateTimeFormatter: DateTimeFormatter?
    ) {
        val reserveringBlaat =
            mutatiesInPeilPeriode.filter { it.rekening.naam == rekening.naam }.sumOf { it.periodeReservering }

        val maandBedrag =
            (rekening.toDTO(periode).budgetMaandBedrag ?: BigDecimal.ZERO)
        val budgetHorizonBedrag = (standInPeriodeService
            .berekenBudgetOpPeilDatum(rekening, budgetHorizon, maandBedrag, reserveringBlaat, periode)
            ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val bedrag = maxOf(budgetHorizonBedrag.min(maandBedrag), BigDecimal.ZERO)
        logger.info(
            "creeerReservingenVoorPeriode: bedrag: $bedrag, rekening: ${rekening.naam}, " + "maandBedrag: $maandBedrag, betaling: $reserveringBlaat, BudgetAanvulling: ${rekening.budgetAanvulling}, " + "budgetHorizon: $budgetHorizon, budgetHorizonBedrag: $budgetHorizonBedrag, " + "periode: ${periode.periodeStartDatum} t/m ${periode.periodeEindDatum} " + "voor ${administratie.naam}"
        )

        val opgeslagenReservering = betalingRepository.findByAdministratieOpDatumBronBestemming(
            administratie = administratie,
            datum = periode.periodeStartDatum,
            reserveringBron = reserveringBufferRekening,
            reserveringBestemming = rekening
        )
        if (bedrag > BigDecimal.ZERO) {
            if (opgeslagenReservering.isEmpty()) {
                logger.info("Nieuwe reservering voor ${rekening.naam} op ${periode.periodeStartDatum} van $bedrag voor ${administratie.naam}")
                betalingRepository.save(
                    Betaling(
                        administratie = administratie,
                        boekingsdatum = periode.periodeStartDatum,
                        reserveringsHorizon = budgetHorizon,
                        bedrag = maxOf(bedrag, BigDecimal.ZERO),
                        omschrijving = "Reservering voor ${rekening.naam} in periode " +
                                "${periode.periodeStartDatum.format(dateTimeFormatter)}/" +
                                "${periode.periodeEindDatum.format(dateTimeFormatter)}",
                        reserveringBron = reserveringBufferRekening,
                        reserveringBestemming = rekening,
                        sortOrder = berekenSortOrder(administratie, periode.periodeStartDatum),
                        betalingsSoort = Betaling.BetalingsSoort.P2P,
                    )
                )
            } else {
                logger.info("Update reservering voor ${rekening.naam} op ${periode.periodeStartDatum} van $bedrag voor ${administratie.naam}")
                betalingRepository.save(
                    opgeslagenReservering[0].fullCopy(
                        bedrag = maxOf(bedrag, BigDecimal.ZERO), reserveringsHorizon = budgetHorizon
                    )
                )
            }
        }
    }

    fun berekenSortOrder(administratie: Administratie, boekingsDatum: LocalDate): String {
        val laatsteSortOrder: String? = betalingRepository.findLaatsteSortOrder(administratie, boekingsDatum)
        val sortOrderDatum = boekingsDatum.toString().replace("-", "")
        logger.info("berekenSortOrder: laatsteSortOrder=$laatsteSortOrder voor administratie=${administratie.naam} op datum=$boekingsDatum")
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