package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.resultaatRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class StandInPeriodeService {
    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

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
        val startSaldiVanPeilPeriode = berekenStartSaldiVanPeilPeriode(peilPeriode)
        val betalingenInPeilPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            peilPeriode.periodeStartDatum,
            peilDatum
        )
        logger.info("betalingenInPeilPeriode: van ${peilPeriode.periodeStartDatum} tot: ${peilDatum} ${betalingenInPeilPeriode.joinToString { it.rekening.naam + ' ' + it.budgetBetaling }}")

        return startSaldiVanPeilPeriode
            .filter { inclusiefOngeldigeRekeningen || it.rekening.rekeningIsGeldigInPeriode(peilPeriode) }
            .sortedBy { it.rekening.sortOrder }
            .map { saldo ->
                val rekening = saldo.rekening
                val betaling = betalingenInPeilPeriode
                    .filter { it.rekening.naam == rekening.naam }
                    .sumOf { it.budgetBetaling }
                val openingsSaldo = saldo.openingsSaldo
                val achterstand = saldo.achterstand

                // eerst de achterstand afbetalen, let op: achterstand is negatief
                val achterstandOpPeilDatum = (achterstand + betaling.abs()).min(BigDecimal.ZERO)
                val betalingNaAflossenAchterstand = (achterstand + betaling.abs()).max(BigDecimal.ZERO)

                val betaaldagInPeriode = if (rekening.budgetBetaalDag != null)
                    periodeService.berekenDagInPeriode(rekening.budgetBetaalDag, peilPeriode)
                else null

                // budgetMaandBedrag is het bedrag dat deze periode moet worden betaald,
                // eventueel aangepast aan de budgetVariabiliteit obv een betaling,
                // eventueel 0 als deze maand geen betaling wordt verwacht,
                // EXCL een eventuele de achterstand
                val budgetMaandBedrag =
                    rekening.toDTO(peilPeriode, betaling.abs()).budgetMaandBedrag ?: BigDecimal.ZERO

                val budgetOpPeilDatum =
                    when (rekening.rekeningGroep.budgetType) {
                        RekeningGroep.BudgetType.VAST, RekeningGroep.BudgetType.SPAREN, RekeningGroep.BudgetType.INKOMSTEN -> {
                            if (betaaldagInPeriode == null) {
                                throw IllegalStateException("Geen budgetBetaalDag voor ${rekening.naam} met RekeningType 'VAST' van ${rekening.rekeningGroep.gebruiker.email}")
                            }
                            if (peilDatum.isAfter(betaaldagInPeriode)) budgetMaandBedrag
                            else (budgetMaandBedrag).min(betaling.abs())
                        }

                        RekeningGroep.BudgetType.CONTINU -> {
                            berekenContinuBudgetOpPeildatum(rekening, peilPeriode, peilDatum)
                        }

                        else -> BigDecimal.ZERO
                    }

                val betaaldBinnenBudget = if (rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                    (budgetMaandBedrag + achterstand.abs()).min(betaling.abs())
                else
                    (budgetOpPeilDatum + achterstand.abs()).min(betaling.abs())
                val meerDanMaandBudget = BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetMaandBedrag)
                val minderDanBudget = BigDecimal.ZERO.max(budgetOpPeilDatum - betalingNaAflossenAchterstand.abs())
                val meerDanBudget = if (rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
                    BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetMaandBedrag - meerDanMaandBudget)
                else
                    BigDecimal.ZERO.max(betalingNaAflossenAchterstand.abs() - budgetOpPeilDatum - meerDanMaandBudget)
                val restMaandBudget =
                    BigDecimal.ZERO.max(budgetMaandBedrag - betalingNaAflossenAchterstand.abs() - minderDanBudget)
                Saldo.SaldoDTO(
                    0,
                    rekening.rekeningGroep.naam,
                    rekening.rekeningGroep.rekeningGroepSoort,
                    rekening.rekeningGroep.budgetType,
                    rekening.naam,
                    aflossing = rekening.aflossing?.toDTO(),
                    spaartegoed = rekening.spaartegoed?.toDTO(),
                    rekening.rekeningGroep.sortOrder * 1000 + rekening.sortOrder,
                    openingsSaldo,
                    achterstand = achterstand,
                    achterstandOpPeilDatum = achterstandOpPeilDatum,
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetBetaalDag = rekening.budgetBetaalDag,
                    budgetPeilDatum = peilDatum.toString(),
                    budgetBetaling = betaling,
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    betaaldBinnenBudget = betaaldBinnenBudget,
                    minderDanBudget = minderDanBudget,
                    meerDanBudget = meerDanBudget,
                    meerDanMaandBudget = meerDanMaandBudget,
                    restMaandBudget = restMaandBudget,
                )
            }
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

    fun berekenMutatieLijstTussenDatums(gebruiker: Gebruiker, vanDatum: LocalDate, totDatum: LocalDate): List<Saldo> {
        val rekeningGroepLijst = rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(gebruiker, vanDatum, totDatum)
        val saldoLijst = rekeningGroepLijst.flatMap { rekeningGroep ->
            rekeningGroep.rekeningen.map { rekening ->
                val budgetBetaling =
                    betalingen.fold(BigDecimal.ZERO) { acc, betaling -> acc + berekenMutaties(betaling, rekening) }

                Saldo(0, rekening, budgetBetaling = budgetBetaling)
            }
        }
        logger.info("mutaties van $vanDatum tot $totDatum #betalingen: ${betalingen.size}: ${saldoLijst.joinToString { "${it.rekening.naam} -> ${it.budgetBetaling}" }}")
        return saldoLijst
    }

    fun berekenMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.bron.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO +
                if (betaling.bestemming.id == rekening.id) betaling.bedrag else BigDecimal.ZERO
    }

    fun berekenStartSaldiVanPeilPeriode(peilPeriode: Periode): List<Saldo> {
        val gebruiker = peilPeriode.gebruiker
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val startDatum = basisPeriode.periodeEindDatum.plusDays(1)
        val eindDatum = peilPeriode.periodeStartDatum.minusDays(1)

        val basisPeriodeSaldi = saldoRepository.findAllByPeriode(basisPeriode)
        val betalingenTussenBasisEnPeilPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            basisPeriode.periodeEindDatum.plusDays(1),
            peilPeriode.periodeStartDatum.minusDays(1)
        )

        val saldoLijst = basisPeriodeSaldi.map { basisPeriodeSaldo: Saldo ->
            val budgetBetaling = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeSaldo.rekening.naam }
                .sumOf { it.budgetBetaling }
            val openingsSaldo =
                if (!resultaatRekeningGroepSoort.contains(basisPeriodeSaldo.rekening.rekeningGroep.rekeningGroepSoort))
                    basisPeriodeSaldo.openingsSaldo + basisPeriodeSaldo.budgetBetaling + budgetBetaling
                else BigDecimal.ZERO

            val aantalGeldigePeriodes = periodeRepository
                .getPeriodesTussenDatums(
                    basisPeriodeSaldo.rekening.rekeningGroep.gebruiker,
                    basisPeriode.periodeStartDatum,
                    peilPeriode.periodeStartDatum.minusDays(1)
                )
                .count {
                    basisPeriodeSaldo.rekening.rekeningIsGeldigInPeriode(it)
                            && basisPeriodeSaldo.rekening.rekeningVerwachtBetalingInPeriode(it)
                }
            val budgetMaandBedrag = basisPeriodeSaldo.rekening.toDTO(peilPeriode).budgetMaandBedrag ?: BigDecimal.ZERO
            val achterstand = BigDecimal.ZERO
//                if (basisPeriodeSaldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
//                    (basisPeriodeSaldo.achterstand
//                            - (BigDecimal(aantalGeldigePeriodes) * budgetMaandBedrag)
//                            + basisPeriodeSaldo.budgetBetaling + budgetBetaling
//                            ).min(BigDecimal.ZERO)
//                else BigDecimal.ZERO
            basisPeriodeSaldo.fullCopy(
                openingsSaldo = openingsSaldo,
                achterstand = achterstand,
            )
        }
        return saldoLijst
    }
}
