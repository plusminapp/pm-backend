package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.filter

@Service
class StandInPeriodeService {
    @Autowired
    lateinit var startSaldiVanPeriodeService: StartSaldiVanPeriodeService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenStandInPeriode(
        peilDatum: LocalDate,
        peilPeriode: Periode,
        inclusiefOngeldigeRekeningen: Boolean = false
    ): List<Saldo.SaldoDTO> {
        val gebruiker = peilPeriode.gebruiker
        val startSaldiVanPeilPeriode = startSaldiVanPeriodeService.berekenStartSaldiVanPeilPeriode(peilPeriode)
        val mutatiesInPeilPeriode =
            startSaldiVanPeriodeService.berekenMutatieLijstTussenDatums(
                gebruiker,
                peilPeriode.periodeStartDatum,
                peilDatum
            )
        val inkomstenInPeilPeriode = mutatiesInPeilPeriode
            .filter { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN }
            .sumOf { it.betaling }
        logger.info("betalingenInPeilPeriode: van ${peilPeriode.periodeStartDatum} tot: $peilDatum ${mutatiesInPeilPeriode.joinToString { it.rekening.naam + ' ' + it.betaling }}")

        return startSaldiVanPeilPeriode
            .filter { inclusiefOngeldigeRekeningen || it.rekening.rekeningIsGeldigInPeriode(peilPeriode) }
            .sortedBy { it.rekening.sortOrder }
            .map { saldo ->
                val rekening = saldo.rekening
                val betaling = mutatiesInPeilPeriode
                    .filter { it.rekening.naam == rekening.naam }
//                    .filter { it.rekening.rekeningGroep.budgetType != RekeningGroep.BudgetType.SPAREN }
                    .sumOf { it.betaling }
                val reservering = mutatiesInPeilPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.reservering } -
                        if (rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER)
                            inkomstenInPeilPeriode
                        else BigDecimal.ZERO
                val openingsBalansSaldo = saldo.openingsBalansSaldo
                val achterstand = saldo.achterstand

                // eerst de achterstand afbetalen, let op: achterstand is negatief
                val achterstandOpPeilDatum = (achterstand + betaling.abs()).min(BigDecimal.ZERO)
                val betalingNaAflossenAchterstand = (achterstand + betaling.abs()).max(BigDecimal.ZERO)

                // budgetMaandBedrag is het bedrag dat deze periode moet worden betaald,
                // eventueel aangepast aan de budgetVariabiliteit o.b.v. een betaling,
                // eventueel 0 als deze maand geen betaling wordt verwacht,
                // EXCL een eventuele de achterstand
                val budgetMaandBedrag =
                    rekening.toDTO(peilPeriode, betaling.abs()).budgetMaandBedrag ?: BigDecimal.ZERO

                val budgetOpPeilDatum =
                    berekenBudgetOpPeilDatum(rekening, peilDatum, budgetMaandBedrag, betaling, peilPeriode)
                        ?: BigDecimal.ZERO

                val betaaldBinnenBudget = if (rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                    (budgetMaandBedrag + achterstand.abs()).min(betaling.abs())
                else {
                    (budgetOpPeilDatum + achterstand.abs()).min(betaling.abs())
                }
                val meerDanMaandBudget = BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetMaandBedrag)
                val minderDanBudget = BigDecimal.ZERO.max(budgetOpPeilDatum - betalingNaAflossenAchterstand.abs())
                val meerDanBudget = if (rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                    BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetMaandBedrag - meerDanMaandBudget)
                else
                    BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetOpPeilDatum - meerDanMaandBudget)
                val restMaandBudget =
                    if (saldo.rekening.rekeningGroep.budgetType != RekeningGroep.BudgetType.SPAREN)
                        BigDecimal.ZERO.max(budgetMaandBedrag - betalingNaAflossenAchterstand.abs() - minderDanBudget + meerDanBudget + meerDanMaandBudget)
                    else
                        BigDecimal.ZERO
                Saldo.SaldoDTO(
                    0,
                    rekening.rekeningGroep.naam,
                    rekening.rekeningGroep.rekeningGroepSoort,
                    rekening.rekeningGroep.budgetType,
                    rekening.naam,
                    aflossing = rekening.aflossing?.toDTO(),
                    spaartegoed = rekening.spaartegoed?.toDTO(),
                    rekening.rekeningGroep.sortOrder * 1000 + rekening.sortOrder,
                    openingsBalansSaldo,
                    openingsReserveSaldo = saldo.openingsReserveSaldo,
                    achterstand = achterstand,
                    achterstandOpPeilDatum = achterstandOpPeilDatum,
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetBetaalDag = rekening.budgetBetaalDag,
                    budgetAanvulling = rekening.budgetAanvulling,
                    budgetPeilDatum = peilDatum.toString(),
                    betaling = betaling,
                    reservering = reservering,
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    betaaldBinnenBudget = betaaldBinnenBudget,
                    minderDanBudget = minderDanBudget,
                    meerDanBudget = meerDanBudget,
                    meerDanMaandBudget = meerDanMaandBudget,
                    restMaandBudget = restMaandBudget,
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
                    val betaaldagInPeriode = if (rekening.budgetBetaalDag != null)
                        periodeService.berekenDagInPeriode(rekening.budgetBetaalDag, peilPeriode)
                    else null

                    if (betaaldagInPeriode == null) {
                        throw IllegalStateException("Geen budgetBetaalDag voor ${rekening.naam} met RekeningType 'VAST' van ${rekening.rekeningGroep.gebruiker.email}")
                    }
                    if (!peilDatum.isBefore(betaaldagInPeriode)) budgetMaandBedrag
                    else (budgetMaandBedrag).min(betaling.abs())
                }

                RekeningGroep.BudgetType.CONTINU -> {
                    berekenContinuBudgetOpPeildatum(rekening, peilPeriode, peilDatum)
                }

                else -> BigDecimal.ZERO
            }
        return budgetOpPeilDatum
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
