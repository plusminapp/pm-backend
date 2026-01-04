package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.berekenDagInPeriode
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class StandInPeriodeService {
    @Autowired
    lateinit var standMutatiesTussenDatumsService: StandMutatiesTussenDatumsService

    @Autowired
    lateinit var standStartVanPeriodeService: StandStartVanPeriodeService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenSaldiOpDatum(
        administratie: Administratie,
        peilDatum: LocalDate,
    ): List<Saldo> {
        val periode = periodeService.getFakePeriode(administratie, peilDatum)
        val vandaag = administratie.vandaag ?: LocalDate.now()
        return berekenSaldiOpDatum(vandaag, periode)
    }

    fun berekenSaldiOpDatum(
        peilDatum: LocalDate,
        periode: Periode,
        inclusiefOngeldigeRekeningen: Boolean = false
    ): List<Saldo> {
        val administratie = periode.administratie
        val startSaldiVanPeriode = standStartVanPeriodeService.berekenStartSaldiVanPeriode(periode)
        val mutatiesInPeriode =
            standMutatiesTussenDatumsService.berekenMutatieLijstTussenDatums(
                administratie,
                periode.periodeStartDatum,
                peilDatum // inclusive
            )
        return startSaldiVanPeriode
            .filter { inclusiefOngeldigeRekeningen || it.rekening.rekeningIsGeldigInPeriode(periode) }
            .sortedBy { it.rekening.sortOrder }
            .map { saldo ->
                val rekening = saldo.rekening
                val periodeBetaling = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.periodeBetaling }
                val periodeReservering = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.periodeReservering }
                // TODO achterstand in periode berekenen
                val achterstand = BigDecimal.ZERO

                val openingsReserveSaldo = saldo.openingsReserveSaldo

                val budgetMaandBedrag =
                    rekening.toDTO(periode, periodeBetaling.abs()).budgetMaandBedrag ?: BigDecimal.ZERO

                Saldo(
                    id = 0,
                    rekening = rekening,
                    openingsBalansSaldo = saldo.openingsBalansSaldo,
                    openingsReserveSaldo = openingsReserveSaldo,
                    openingsAchterstand = saldo.openingsAchterstand,
                    periodeBetaling = periodeBetaling,
                    periodeReservering = periodeReservering,
                    periodeAchterstand = achterstand,
                    budgetMaandBedrag = budgetMaandBedrag,
                    correctieBoeking = BigDecimal.ZERO,
                    periode = periode
                )
            }
    }

    fun berekenBudgetOpPeilDatum(
        rekening: Rekening,
        peilDatum: LocalDate,
        budgetMaandBedrag: BigDecimal,
        betaling: BigDecimal,
        peilPeriode: Periode
    ): BigDecimal? {
        val budgetOpPeilDatum =
            when (rekening.rekeningGroep.budgetType) {
                RekeningGroep.BudgetType.VAST, RekeningGroep.BudgetType.INKOMSTEN -> {
                    berekenVastBudgetOpPeildatum(
                        rekening,
                        peilPeriode,
                        rekening.rekeningGroep.budgetType,
                        peilDatum,
                        budgetMaandBedrag,
                        betaling
                    )
                }

                RekeningGroep.BudgetType.CONTINU -> {
                    berekenContinuBudgetOpPeildatum(rekening, peilPeriode, peilDatum)
                }

                else -> BigDecimal.ZERO
            }
        return budgetOpPeilDatum
    }

    private fun berekenVastBudgetOpPeildatum(
        rekening: Rekening,
        peilPeriode: Periode,
        budgetType: RekeningGroep.BudgetType,
        peilDatum: LocalDate,
        budgetMaandBedrag: BigDecimal,
        betaling: BigDecimal
    ): BigDecimal? {
        val betaaldagInPeriode = if (rekening.budgetBetaalDag != null)
            peilPeriode.berekenDagInPeriode(rekening.budgetBetaalDag)
        else null

        if (betaaldagInPeriode == null) {
            throw PM_GeenBetaaldagException(
                listOf(
                    rekening.naam,
                    budgetType.name,
                    rekening.rekeningGroep.administratie.naam
                )
            )
        }
        return if (!peilDatum.isBefore(betaaldagInPeriode)) budgetMaandBedrag
        else (budgetMaandBedrag).min(betaling.abs())
    }

    fun berekenContinuBudgetOpPeildatum(
        rekening: Rekening,
        gekozenPeriode: Periode,
        peilDatum: LocalDate
    ): BigDecimal {
        if (peilDatum < gekozenPeriode.periodeStartDatum) return BigDecimal.ZERO
        val dagenInPeriode: Long =
            gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        val budgetMaandBedrag = when (rekening.budgetPeriodiciteit) {
            Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag
                ?.times(BigDecimal(dagenInPeriode))
                ?.div(BigDecimal(7)) ?: BigDecimal.ZERO

            Rekening.BudgetPeriodiciteit.MAAND -> rekening.budgetBedrag ?: BigDecimal.ZERO
            null -> BigDecimal.ZERO
        }
        if (peilDatum >= gekozenPeriode.periodeEindDatum) return budgetMaandBedrag
        val dagenTotPeilDatum: Long = peilDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        return (budgetMaandBedrag.times(BigDecimal(dagenTotPeilDatum)).div(BigDecimal(dagenInPeriode)))
    }
}
