package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.resultaatRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.PeriodeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class SaldoResultaatService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenSaldoResultaatOpDatum(
        gebruiker: Gebruiker,
        peilDatum: LocalDate,
        openingsBalans: List<Saldo.SaldoDTO>
    ): List<Saldo.SaldoDTO> {
        val saldoPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val gekozenPeriode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, peilDatum) ?: run {
            logger.error("Geen periode voor ${gebruiker.bijnaam} op ${peilDatum}, gebruik ${saldoPeriode.periodeStartDatum}")
            saldoPeriode
        }
        val rekeningenLijst = rekeningRepository
            .findRekeningGroepenVoorGebruiker(gebruiker)
            .flatMap { it.rekeningen }
            .filter { rekening -> resultaatRekeningGroepSoort.contains(rekening.rekeningGroep.rekeningGroepSoort) }
            .filter { rekening -> rekeningIsGeldigInPeriode(rekening, gekozenPeriode) }
        logger.info("RekeningenLijst: ${rekeningenLijst.joinToString { it.naam + '/' + it.vanPeriode?.periodeStartDatum.toString() + '/' + it.totEnMetPeriode?.periodeStartDatum.toString() }} voor periodeStartDatum ${gekozenPeriode.periodeStartDatum} ")
        return rekeningenLijst
            .sortedBy { it.sortOrder }
            .map { rekening ->
                val budgetBetaling = getBetalingVoorRekeningInPeriode(
                    rekening,
                    gekozenPeriode
                ) // negatief voor uitgaven, positief voor inkomsten
                val saldo = openingsBalans
                    .find { it.rekeningNaam == rekening.naam }
                val isRekeningVasteLastOfAflossing = rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN &&
                        rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST ||
                        rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING
                val dagInPeriode = if (rekening.budgetBetaalDag != null) periodeService.berekenDagInPeriode(rekening.budgetBetaalDag, gekozenPeriode) else null
                val wordtDezeMaandBetalingVerwacht =
                    rekening.maanden.isNullOrEmpty() || rekening.maanden!!.contains(dagInPeriode?.monthValue)
                val isBedragBinnenVariabiliteit =
                    budgetBetaling.abs() <= rekening.toDTO(gekozenPeriode).budgetMaandBedrag?.times(
                        BigDecimal(100 + (rekening.budgetVariabiliteit ?: 0)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                    ) &&
                            budgetBetaling.abs() >= rekening.toDTO(gekozenPeriode).budgetMaandBedrag?.times(
                                BigDecimal(100 - (rekening.budgetVariabiliteit ?: 0)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
                            )
                // VasteLastenRekening is Betaald als de rekening een vaste lasten rekening is, en óf geen betaling wordt verwacht, óf de betaling binnen de budgetVariabiliteit valt
                val isVasteLastenRekeningBetaald = isRekeningVasteLastOfAflossing && (!wordtDezeMaandBetalingVerwacht || isBedragBinnenVariabiliteit)

                val budgetMaandBedrag = berekenBudgetMaandBedrag(rekening, gekozenPeriode)
                val budgetOpPeilDatum = if (!wordtDezeMaandBetalingVerwacht) BigDecimal(0)
                    else berekenBudgetOpPeildatum(rekening, gekozenPeriode, peilDatum) ?: BigDecimal(0)
                val meerDanMaandBudget =
                    if (isVasteLastenRekeningBetaald) BigDecimal(0)
                    else BigDecimal(0).max(budgetBetaling.abs() - budgetMaandBedrag)
                val minderDanBudget =
                    if (isVasteLastenRekeningBetaald) BigDecimal(0)
                    else BigDecimal(0).max(budgetOpPeilDatum.minus(budgetBetaling.abs()))
                val meerDanBudget =
                    if (isVasteLastenRekeningBetaald) BigDecimal(0)
                    else BigDecimal(0).max(budgetBetaling.abs() - budgetOpPeilDatum - meerDanMaandBudget)
                Saldo.SaldoDTO(
                    0,
                    rekening.rekeningGroep.naam,
                    rekening.rekeningGroep.rekeningGroepSoort,
                    rekening.rekeningGroep.budgetType,
                    rekening.naam,
                    rekening.rekeningGroep.sortOrder * 1000 + rekening.sortOrder,
                    saldo?.saldo?.minus(budgetBetaling) ?: BigDecimal(0),
                    achterstand = saldo?.achterstand ?: BigDecimal(0),
                    // TODO: achterstandNu berekenen obv aflossing moet wel/niet betaald zijn
                    achterstandNu =
                        ((saldo?.achterstand ?: BigDecimal(0)) + budgetOpPeilDatum + budgetBetaling).max(
                            BigDecimal(0)
                        ),
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetPeilDatum = peilDatum.toString(),
                    budgetBetaling = budgetBetaling,
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    betaaldBinnenBudget = budgetOpPeilDatum.min(budgetBetaling.abs()),
                    minderDanBudget = minderDanBudget,
                    meerDanBudget = meerDanBudget,
                    meerDanMaandBudget = meerDanMaandBudget,
                    restMaandBudget =
                        if (isVasteLastenRekeningBetaald) BigDecimal(0)
                        else BigDecimal(0).max(budgetMaandBedrag - budgetBetaling.abs() - minderDanBudget),
                )
            }
    }

    fun getBetalingVoorRekeningInPeriode(rekening: Rekening, periode: Periode): BigDecimal {
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(
            rekening.rekeningGroep.gebruiker,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        )
        val filteredBetalingen = betalingen.filter { it.bron.id == rekening.id || it.bestemming.id == rekening.id }
        val bedrag =
            filteredBetalingen.fold(BigDecimal(0)) { acc, betaling -> if (betaling.bron.id == rekening.id) acc + betaling.bedrag else acc - betaling.bedrag }
        logger.info("Betaling voor rekening ${rekening.naam} in periode ${periode.periodeStartDatum} tot ${periode.periodeEindDatum}: $bedrag met filteredBetalingen: ${filteredBetalingen.size}")
        return bedrag
    }

    fun berekenBudgetMaandBedrag(rekening: Rekening, gekozenPeriode: Periode): BigDecimal {
        val dagenInPeriode: Long =
            gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        return when (rekening.budgetPeriodiciteit) {
            Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag?.times(BigDecimal(dagenInPeriode))
                ?.div(BigDecimal(7))

            Rekening.BudgetPeriodiciteit.MAAND -> rekening.budgetBedrag
            null -> BigDecimal(0)
        }?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal(0)
    }

    fun berekenBudgetOpPeildatum(rekening: Rekening, gekozenPeriode: Periode, peilDatum: LocalDate): BigDecimal? {
        when (rekening.rekeningGroep.budgetType) {
            RekeningGroep.BudgetType.VAST -> {
                if (rekening.budgetBetaalDag == null) {
                    throw IllegalStateException("Geen budgetBetaalDag voor ${rekening.naam} met RekeningType 'VAST' van ${rekening.rekeningGroep.gebruiker.email}")
                }
                val betaaldagInPeriode =
                    if (rekening.budgetBetaalDag < gekozenPeriode.periodeStartDatum.dayOfMonth) {
                        gekozenPeriode.periodeStartDatum.plusMonths(1).withDayOfMonth(rekening.budgetBetaalDag)
                    } else {
                        gekozenPeriode.periodeStartDatum.withDayOfMonth(rekening.budgetBetaalDag)
                    }
                logger.info(
                    "betaaldagInPeriode: $betaaldagInPeriode, peilDatum: $peilDatum, after: ${
                        betaaldagInPeriode.isAfter(
                            peilDatum
                        )
                    }"
                )
                return if (betaaldagInPeriode.isAfter(peilDatum)) BigDecimal(0) else rekening.budgetBedrag
            }

            RekeningGroep.BudgetType.CONTINU -> {
                if (peilDatum < gekozenPeriode.periodeStartDatum) {
                    return BigDecimal(0)
                }
                val dagenInPeriode: Long =
                    gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
                val budgetMaandBedrag = when (rekening.budgetPeriodiciteit) {
                    Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag?.times(BigDecimal(dagenInPeriode))
                        ?.div(BigDecimal(7))

                    Rekening.BudgetPeriodiciteit.MAAND -> rekening.budgetBedrag
                    null -> BigDecimal(0)
                }
                if (peilDatum >= gekozenPeriode.periodeEindDatum) {
                    return budgetMaandBedrag
                }
                val dagenTotPeilDatum: Long = peilDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
                logger.info(
                    "Rekening ${rekening.naam} van ${gekozenPeriode.periodeStartDatum} tot " +
                            "$peilDatum met maandRekening $budgetMaandBedrag: $dagenTotPeilDatum/$dagenInPeriode = " +
                            "${
                                (budgetMaandBedrag?.times(BigDecimal(dagenTotPeilDatum))
                                    ?.div(BigDecimal(dagenInPeriode)))
                            }"
                )
                return (budgetMaandBedrag?.times(BigDecimal(dagenTotPeilDatum))?.div(BigDecimal(dagenInPeriode)))
            }

            else -> {
                throw IllegalStateException("RekeningGroep ${rekening.rekeningGroep.naam} heeft geen BudgetType")
            }
        }
    }

    fun berekenResultaatSamenvatting(
        periode: Periode,
        peilDatum: LocalDate,
        saldi: List<Saldo.SaldoDTO>,
    ): Saldo.ResultaatSamenvattingOpDatumDTO {
        logger.info("berekenRekeningSamenvatting ${saldi.joinToString { it.toString() }}")
        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val periodeVoorbij = peilDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val percentagePeriodeVoorbij = 100 * periodeVoorbij / periodeLengte
        val isPeriodeVoorbij = peilDatum >= periode.periodeEindDatum
        val budgetMaandInkomsten = saldi
            .filter {
                it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN
            }
            .fold(BigDecimal(0)) { acc, saldoDTO -> acc + (saldoDTO.budgetMaandBedrag) }
        val werkelijkeMaandInkomsten = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN }
            .fold(BigDecimal(0)) { acc, saldoDTO -> acc + (saldoDTO.budgetBetaling) }
        val maandInkomstenBedrag = budgetMaandInkomsten.max(werkelijkeMaandInkomsten)
        logger.info("budgetMaandInkomsten: $budgetMaandInkomsten, werkelijkeMaandInkomsten: $werkelijkeMaandInkomsten, maandInkomstenBedrag: $maandInkomstenBedrag")

        val besteedTotPeilDatum = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN }
            .fold(BigDecimal(0)) { acc, saldoDTO -> acc - (saldoDTO.budgetBetaling) }

        val nogNodigNaPeilDatum = if (isPeriodeVoorbij) BigDecimal(0) else {
            saldi
                .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN }
                .fold(BigDecimal(0)) { acc, saldoDTO ->
                    val restMaandRekening = if (saldoDTO.budgetType == RekeningGroep.BudgetType.CONTINU)
                        (saldoDTO.budgetMaandBedrag) - (saldoDTO.betaaldBinnenBudget ?: BigDecimal(0))
                    else (saldoDTO.restMaandBudget ?: BigDecimal(0)) + (saldoDTO.minderDanBudget ?: BigDecimal(0))
                    logger.info("RekeningNodigNaPeilDatum: ${saldoDTO.rekeningGroepNaam} $restMaandRekening")
                    acc + restMaandRekening
                }
        }

        val actueleBuffer = maandInkomstenBedrag - besteedTotPeilDatum - nogNodigNaPeilDatum
        return Saldo.ResultaatSamenvattingOpDatumDTO(
            percentagePeriodeVoorbij = percentagePeriodeVoorbij,
            budgetMaandInkomstenBedrag =
                if (isPeriodeVoorbij)
                    werkelijkeMaandInkomsten
                else budgetMaandInkomsten.max(werkelijkeMaandInkomsten),
            besteedTotPeilDatum = besteedTotPeilDatum,
            nogNodigNaPeilDatum = nogNodigNaPeilDatum,
            actueleBuffer = actueleBuffer
        )
    }

    fun rekeningIsGeldigInPeriode(rekening: Rekening, periode: Periode): Boolean {
        return (rekening.vanPeriode == null || periode.periodeStartDatum >= rekening.vanPeriode.periodeStartDatum) &&
                (rekening.totEnMetPeriode == null || periode.periodeEindDatum <= rekening.totEnMetPeriode.periodeEindDatum)
    }
}

