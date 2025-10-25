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
        gebruiker: Gebruiker,
        peilDatum: LocalDate,
    ): List<Saldo.SaldoDTO> {
        val periode = periodeService.getPeriode(gebruiker, peilDatum)
        return berekenSaldiOpDatum(LocalDate.now(),periode)
    }

    fun berekenSaldiOpDatum(
        peilDatum: LocalDate,
        periode: Periode,
        inclusiefOngeldigeRekeningen: Boolean = false
    ): List<Saldo.SaldoDTO> {

        val gebruiker = periode.gebruiker
        val startSaldiVanPeriode = standStartVanPeriodeService.berekenStartSaldiVanPeriode(periode)

        val mutatiesInPeriode =
            standMutatiesTussenDatumsService.berekenMutatieLijstTussenDatums(
                gebruiker,
                periode.periodeStartDatum,
                peilDatum
            )

        return startSaldiVanPeriode
            .filter { inclusiefOngeldigeRekeningen || it.rekening.rekeningIsGeldigInPeriode(periode) }
            .sortedBy { it.rekening.sortOrder }
            .map { saldo ->
                val rekening = saldo.rekening
                val betaling = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.betaling }
                val reservering = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.reservering }
                val opgenomenSaldo = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.opgenomenSaldo }

                val openingsBalansSaldo = saldo.openingsBalansSaldo
                val openingsReserveSaldo =
                    if (rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARREKENING)
                        saldo.openingsBalansSaldo + saldo.opgenomenSaldo
                    else saldo.openingsReserveSaldo
                val openingsOpgenomenSaldo = saldo.openingsOpgenomenSaldo + saldo.opgenomenSaldo

                val achterstand = saldo.achterstand

                // eerst de achterstand afbetalen, let op: achterstand is negatief
                val achterstandOpPeilDatum = (achterstand + betaling.abs()).min(BigDecimal.ZERO)
                val betalingNaAflossenAchterstand = (achterstand + betaling.abs()).max(BigDecimal.ZERO)

                // budgetMaandBedrag is het bedrag dat deze periode moet worden betaald,
                // eventueel aangepast aan de budgetVariabiliteit o.b.v. een betaling,
                // eventueel 0 als deze maand geen betaling wordt verwacht,
                // EXCL een eventuele de achterstand
                val budgetMaandBedrag =
                    rekening.toDTO(periode, betaling.abs()).budgetMaandBedrag ?: BigDecimal.ZERO

                val budgetOpPeilDatum =
                    berekenBudgetOpPeilDatum(rekening, peilDatum, budgetMaandBedrag, betaling, periode)
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
                    id= 0,
                    rekeningGroepNaam = rekening.rekeningGroep.naam,
                    rekeningGroepSoort = rekening.rekeningGroep.rekeningGroepSoort,
                    budgetType = rekening.rekeningGroep.budgetType,
                    rekeningNaam = rekening.naam,
                    aflossing = rekening.aflossing?.toDTO(),
                    spaartegoed = rekening.spaartegoed?.toDTO(),
                    sortOrder = rekening.rekeningGroep.sortOrder * 1000 + rekening.sortOrder,
                    openingsBalansSaldo = openingsBalansSaldo,
                    openingsReserveSaldo = openingsReserveSaldo,
                    openingsOpgenomenSaldo = openingsOpgenomenSaldo,
                    achterstand = achterstand,
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetBetaalDag = rekening.budgetBetaalDag,
                    budgetAanvulling = rekening.budgetAanvulling,
                    betaling = betaling,
                    reservering = reservering,
                    opgenomenSaldo = opgenomenSaldo,
                    correctieBoeking = BigDecimal.ZERO,
                    achterstandOpPeilDatum = achterstandOpPeilDatum,
                    budgetPeilDatum = peilDatum.toString(),
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
                        peilPeriode.berekenDagInPeriode(rekening.budgetBetaalDag)
                    else null

                    if (betaaldagInPeriode == null) {
                        throw PM_GeenBetaaldagException(
                            listOf(
                                rekening.naam,
                                rekening.rekeningGroep.budgetType.name,
                                rekening.rekeningGroep.gebruiker.bijnaam
                            )
                        )
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
