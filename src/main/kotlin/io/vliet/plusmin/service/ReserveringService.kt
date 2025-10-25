package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMiddelenRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.isPotjeVoorNu
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesVoorNuRekeningGroepSoort
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

    fun creeerReserveringen(gebruiker: Gebruiker) {
        val periodes = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .sortedBy { it.periodeStartDatum }
            .drop(1)
        periodes.forEach { periode ->
            creeerReserveringenVoorPeriode(gebruiker, periode)
        }
    }

    fun creeerReserveringenVoorPeriode(gebruiker: Gebruiker, periodeId: Long) {
        val periode = periodeService.getPeriode(periodeId, gebruiker)
        creeerReserveringenVoorPeriode(gebruiker, periode)
    }

    fun creeerReserveringenVoorPeriode(gebruiker: Gebruiker, periode: Periode) {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        val reserveringBufferRekening = rekeningRepository.findBufferRekeningVoorGebruiker(gebruiker)
            ?: throw PM_BufferRekeningNotFoundException(listOf(gebruiker.bijnaam))

        val initieleStartSaldiVanPeriode: List<Saldo> =
            standStartVanPeriodeService.berekenStartSaldiVanPeriode(periode)
        val initieleBuffer =
            initieleStartSaldiVanPeriode.find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }?.openingsReserveSaldo
                ?: BigDecimal.ZERO
        val initieleReserveringTekorten =
            initieleStartSaldiVanPeriode.filter { potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
                .sumOf { if (it.openingsReserveSaldo < BigDecimal.ZERO) it.openingsReserveSaldo else BigDecimal.ZERO }
        logger.info("Initiele buffer bij start van periode ${periode.periodeStartDatum} voor ${gebruiker.bijnaam} is $initieleBuffer, reserveringstekorten $initieleReserveringTekorten, delta ${initieleBuffer + initieleReserveringTekorten}.")
        if (initieleBuffer + initieleReserveringTekorten < BigDecimal.ZERO) throw PM_OnvoldoendeBufferSaldoException(
            listOf(
                initieleBuffer.toString(),
                periode.periodeStartDatum.toString(),
                gebruiker.bijnaam,
                initieleReserveringTekorten.toString()
            )
        )

        val startSaldiVanPeriode = if (initieleReserveringTekorten == BigDecimal.ZERO) initieleStartSaldiVanPeriode
        else {
            initieleStartSaldiVanPeriode.map {
                if (potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.openingsReserveSaldo < BigDecimal.ZERO) {
                    betalingRepository.save(
                        Betaling(
                            gebruiker = gebruiker,
                            boekingsdatum = periode.periodeStartDatum.minusDays(1),
                            reserveringsHorizon = periode.periodeStartDatum.minusDays(1),
                            bedrag = -it.openingsReserveSaldo,
                            omschrijving = "Correctie voor ${it.rekening.naam} in periode ${
                                periode.periodeStartDatum.format(
                                    dateTimeFormatter
                                )
                            }",
                            reserveringBron = reserveringBufferRekening,
                            reserveringBestemming = it.rekening,
                            sortOrder = berekenSortOrder(gebruiker, periode.periodeStartDatum.minusDays(1)),
                            betalingsSoort = Betaling.BetalingsSoort.P2P,
                        )
                    )
                    logger.warn("Buffer reserveringstekort van ${-it.openingsReserveSaldo} voor ${it.rekening.naam} bij start van periode ${periode.periodeStartDatum} voor ${gebruiker.bijnaam} aangevuld vanuit buffer.")
                    it.fullCopy(openingsReserveSaldo = BigDecimal.ZERO)
                } else if (it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER) {
                    it.fullCopy(openingsReserveSaldo = it.openingsReserveSaldo + initieleReserveringTekorten)
                } else it
            }
        }
        val rekeningGroepen = rekeningUtilitiesService.findRekeningGroepenMetGeldigeRekeningen(gebruiker, periode)
        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val continueBudgetUitgaven = cashflowService.budgetContinueUitgaven(rekeningGroepen, periodeLengte)
        val betalingenInPeriode = betalingRepository.findAllByGebruikerTussenDatums(
            gebruiker,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        ).filter {
            it.bron?.rekeningGroep?.budgetType !== RekeningGroep.BudgetType.SPAREN && it.bestemming?.rekeningGroep?.budgetType !== RekeningGroep.BudgetType.SPAREN
        }

        var reserveringsDatum = periode.periodeStartDatum
        var bufferSaldo =
            startSaldiVanPeriode
                .find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
                ?.openingsReserveSaldo
                ?: BigDecimal.ZERO
        while (bufferSaldo >= BigDecimal.ZERO && reserveringsDatum.isBefore(periode.periodeEindDatum.plusDays(1))) {
            val nextBufferSaldo = bufferSaldo + continueBudgetUitgaven +
                    cashflowService.budgetVasteLastenUitgaven(rekeningGroepen, reserveringsDatum) +
                    cashflowService.betaaldeInkomsten(betalingenInPeriode, reserveringsDatum)
            if (nextBufferSaldo < BigDecimal.ZERO) break
            bufferSaldo = nextBufferSaldo
            reserveringsDatum = reserveringsDatum.plusDays(1)
        }
        val budgetHorizon = reserveringsDatum.minusDays(1)
        logger.info("POST: Buffer saldo op ${budgetHorizon} is $bufferSaldo, voor ${gebruiker.bijnaam}")

        val rekeningen = rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
        val mutatiesInPeilPeriode = standMutatiesTussenDatumsService.berekenMutatieLijstTussenDatums(
            gebruiker, periode.periodeStartDatum, budgetHorizon
        )

        rekeningen.filter { potjesVoorNuRekeningGroepSoort.contains(it.rekeningGroep.rekeningGroepSoort) }
            .map { rekening ->
                creerReserveringVoorRekening(
                    mutatiesInPeilPeriode,
                    rekening,
                    startSaldiVanPeriode,
                    periode,
                    budgetHorizon,
                    gebruiker,
                    reserveringBufferRekening,
                    dateTimeFormatter
                )
            }
    }

    private fun creerReserveringVoorRekening(
        mutatiesInPeilPeriode: List<Saldo>,
        rekening: Rekening,
        startSaldiVanPeriode: List<Saldo>,
        periode: Periode,
        budgetHorizon: LocalDate,
        gebruiker: Gebruiker,
        reserveringBufferRekening: Rekening,
        dateTimeFormatter: DateTimeFormatter?
    ) {
        val reserveringBlaat =
            mutatiesInPeilPeriode.filter { it.rekening.naam == rekening.naam }.sumOf { it.reservering }

        val verrekenbareStartReservering =
            if (rekening.budgetAanvulling == Rekening.BudgetAanvulling.MET) BigDecimal.ZERO
            else startSaldiVanPeriode.find { it.rekening.id == rekening.id }?.openingsReserveSaldo ?: BigDecimal.ZERO

        val maandBedrag =
            (rekening.toDTO(periode).budgetMaandBedrag ?: BigDecimal.ZERO).minus(verrekenbareStartReservering)
        val budgetHorizonBedrag = (standInPeriodeService
            .berekenBudgetOpPeilDatum(rekening, budgetHorizon, maandBedrag, reserveringBlaat, periode)
            ?.minus(verrekenbareStartReservering)
            ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val bedrag = maxOf(budgetHorizonBedrag.min(maandBedrag), BigDecimal.ZERO)
        logger.info(
            "creeerReservingenVoorPeriode: bedrag: $bedrag, rekening: ${rekening.naam}, " + "maandBedrag: $maandBedrag, betaling: $reserveringBlaat, BudgetAanvulling: ${rekening.budgetAanvulling}, " + "budgetHorizon: $budgetHorizon, budgetHorizonBedrag: $budgetHorizonBedrag, " + "periode: ${periode.periodeStartDatum} t/m ${periode.periodeEindDatum} " + "voor ${gebruiker.bijnaam}"
        )

        val opgeslagenReservering = betalingRepository.findByGebruikerOpDatumBronBestemming(
            gebruiker = gebruiker,
            datum = periode.periodeStartDatum,
            reserveringBron = reserveringBufferRekening,
            reserveringBestemming = rekening
        )
        if (bedrag > BigDecimal.ZERO) {
            if (opgeslagenReservering.isEmpty()) {
                logger.info("Nieuwe reservering voor ${rekening.naam} op ${periode.periodeStartDatum} van $bedrag voor ${gebruiker.bijnaam}")
                betalingRepository.save(
                    Betaling(
                        gebruiker = gebruiker,
                        boekingsdatum = periode.periodeStartDatum,
                        reserveringsHorizon = budgetHorizon,
                        bedrag = maxOf(bedrag, BigDecimal.ZERO),
                        omschrijving = "Buffer voor ${rekening.naam} in periode " + "${
                            periode.periodeStartDatum.format(dateTimeFormatter)
                        }/" + "${periode.periodeEindDatum.format(dateTimeFormatter)}",
                        reserveringBron = reserveringBufferRekening,
                        reserveringBestemming = rekening,
                        sortOrder = berekenSortOrder(gebruiker, periode.periodeStartDatum),
                        betalingsSoort = Betaling.BetalingsSoort.P2P,
                    )
                )
            } else {
                logger.info("Update reservering voor ${rekening.naam} op ${periode.periodeStartDatum} van $bedrag voor ${gebruiker.bijnaam}")
                betalingRepository.save(
                    opgeslagenReservering[0].fullCopy(
                        bedrag = maxOf(bedrag, BigDecimal.ZERO), reserveringsHorizon = budgetHorizon
                    )
                )
            }
        }
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
    fun updateOpeningsReserveringsSaldo(gebruiker: Gebruiker) {
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val basisPeriodeSaldi = saldoRepository.findAllByPeriode(basisPeriode)

        val saldoBetaalMiddelen = basisPeriodeSaldi
            .filter { betaalMiddelenRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.openingsBalansSaldo + it.correctieBoeking}
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
            ?: throw PM_GeenBufferVoorSaldoException(listOf(basisPeriode.id.toString(), gebruiker.bijnaam))
        saldoRepository.save(
            bufferReserveSaldo.fullCopy(
                openingsReserveSaldo = saldoBetaalMiddelen - saldoPotjesVoorNu
            )
        )
    }

}