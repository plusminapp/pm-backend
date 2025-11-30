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
    ): List<Saldo.SaldoDTO> {
        val periode = periodeService.getPeriode(administratie, peilDatum)
        val vandaag = administratie.vandaag ?: LocalDate.now()
        return berekenSaldiOpDatum(vandaag, periode)
    }

    fun berekenSaldiOpDatum(
        peilDatum: LocalDate,
        periode: Periode,
        inclusiefOngeldigeRekeningen: Boolean = false
    ): List<Saldo.SaldoDTO> {

        val administratie = periode.administratie
        val startSaldiVanPeriode = standStartVanPeriodeService.berekenStartSaldiVanPeriode(periode)

        val mutatiesInPeriode =
            standMutatiesTussenDatumsService.berekenMutatieLijstTussenDatums(
                administratie,
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
                    .sumOf { it.periodeBetaling }
                val reservering = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.periodeReservering }
                val opgenomenSaldo = mutatiesInPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.periodeOpgenomenSaldo }
                // TODO achterstand in periode berekenen

                val openingsBalansSaldo = saldo.openingsBalansSaldo
                val openingsReserveSaldo =
                    if (rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARREKENING)
                        saldo.openingsBalansSaldo + saldo.periodeOpgenomenSaldo
                    else saldo.openingsReserveSaldo
                val openingsOpgenomenSaldo = saldo.openingsOpgenomenSaldo + saldo.periodeOpgenomenSaldo

                val openingsAchterstand = saldo.openingsAchterstand

                // eerst de achterstand afbetalen, let op: achterstand is negatief
                val achterstandOpPeilDatum = (openingsAchterstand + betaling.abs()).min(BigDecimal.ZERO)
                val betalingNaAflossenAchterstand = (openingsAchterstand + betaling.abs()).max(BigDecimal.ZERO)

                // budgetMaandBedrag is het bedrag dat deze periode moet worden betaald,
                // eventueel aangepast aan de budgetVariabiliteit o.b.v. een betaling,
                // eventueel 0 als deze maand geen betaling wordt verwacht,
                // EXCL een eventuele de achterstand
                val budgetMaandBedrag =
                    rekening.toDTO(periode, betaling.abs()).budgetMaandBedrag ?: BigDecimal.ZERO

                val budgetOpPeilDatum =
                    berekenBudgetOpPeilDatum(rekening, peilDatum, budgetMaandBedrag, betaling, periode)
                        ?: BigDecimal.ZERO
                // Start obsolete code
                val betaaldBinnenBudget = if (rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                    (budgetMaandBedrag + openingsAchterstand.abs()).min(betaling.abs())
                else {
                    (budgetOpPeilDatum + openingsAchterstand.abs()).min(betaling.abs())
                }
                val meerDanMaandBudget = BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetMaandBedrag)
                val minderDanBudget = BigDecimal.ZERO.max(budgetOpPeilDatum - betalingNaAflossenAchterstand.abs())
                val meerDanBudget = if (rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                    BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetMaandBedrag - meerDanMaandBudget)
                else
                    BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetOpPeilDatum - meerDanMaandBudget)
                logger.info("KomtNogNodig berekening voor rekening ${rekening.naam}: " +
                        "saldo.rekening.rekeningGroep.budgetType: ${saldo.rekening.rekeningGroep.budgetType}, " +
                        "isVAST: ${saldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST}, " +
                        "isCONTINU: ${saldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.CONTINU}, " +
                        "budgetMaandBedrag: $budgetMaandBedrag, " +
                        "budgetOpPeilDatum: $budgetOpPeilDatum, " +
                        "betaling: $betaling")
                val komtNogNodig =
                    if (saldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                        BigDecimal.ZERO.max(budgetMaandBedrag - budgetOpPeilDatum)
                    else if (saldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.CONTINU)
                        BigDecimal.ZERO.max(budgetOpPeilDatum - betaling)
                    else
                        BigDecimal.ZERO
                Saldo.SaldoDTO(
                    id = 0,
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
                    openingsAchterstand = openingsAchterstand,
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetBetaalDag = rekening.budgetBetaalDag,
                    budgetAanvulling = rekening.budgetAanvulling,
                    periodeBetaling = betaling,
                    periodeReservering = reservering,
                    periodeOpgenomenSaldo = opgenomenSaldo,
                    correctieBoeking = BigDecimal.ZERO,
                    periodeAchterstand = achterstandOpPeilDatum,
                    peilDatum = peilDatum.toString(),
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    betaaldBinnenBudget = betaaldBinnenBudget,
                    minderDanBudget = minderDanBudget,
                    meerDanBudget = meerDanBudget,
                    meerDanMaandBudget = meerDanMaandBudget,
                    komtNogNodig = komtNogNodig,
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
                                rekening.rekeningGroep.administratie.naam
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
