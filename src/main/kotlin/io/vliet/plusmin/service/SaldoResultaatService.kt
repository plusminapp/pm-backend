package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.resultaatRekeningGroepSoort
//import io.vliet.plusmin.domain.Rekening.RekeningSamenvattingDTO
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

    fun berekenSaldoResultaatOpDatum(gebruiker: Gebruiker, peilDatum: LocalDate): List<Saldo.SaldoDTO> {
        val saldoPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val gekozenPeriode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, peilDatum) ?: run {
            logger.error("Geen periode voor ${gebruiker.bijnaam} op ${peilDatum}, gebruik ${saldoPeriode.periodeStartDatum}")
            saldoPeriode
        }
        val rekeningenLijst = rekeningRepository
            .findRekeningGroepenVoorGebruiker(gebruiker)
            .flatMap { it.rekeningen }
            .filter { rekening -> resultaatRekeningGroepSoort.contains(rekening.rekeningGroep.rekeningGroepSoort) }
            .filter { rekening -> RekeningIsGeldigInPeriode(rekening, gekozenPeriode) }
        logger.info("RekeningenLijst: ${rekeningenLijst.joinToString { it.naam + '/' + it.vanPeriode?.periodeStartDatum.toString() + '/' + it.totEnMetPeriode?.periodeStartDatum.toString() }} voor periodeStartDatum ${gekozenPeriode.periodeStartDatum} ")
        return rekeningenLijst
            .sortedBy { it.sortOrder }
            .map { rekening ->
                val budgetBetaling = getBetalingVoorRekeningInPeriode(rekening, gekozenPeriode)
                val isVasteLastenRekeningBetaald =
                    budgetBetaling > BigDecimal(0) &&
                            rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST &&
                            rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN
                val budgetMaandBedrag = berekenMaandRekening(rekening, gekozenPeriode)
                val budgetOpPeilDatum = berekenBudgetOpPeildatum(rekening, gekozenPeriode, peilDatum) ?: BigDecimal(0)
                val meerDanMaandRekening =
                    if (isVasteLastenRekeningBetaald) BigDecimal(0)
                    else BigDecimal(0).max(budgetBetaling - budgetMaandBedrag)
                val minderDanRekening =
                    if (isVasteLastenRekeningBetaald) BigDecimal(0)
                    else BigDecimal(0).max(budgetOpPeilDatum?.minus(budgetBetaling))
                val meerDanRekening =
                    if (isVasteLastenRekeningBetaald) BigDecimal(0)
                    else BigDecimal(0).max(budgetBetaling - budgetOpPeilDatum - meerDanMaandRekening)
                Saldo.SaldoDTO(
                    0,
                    rekening.rekeningGroep.naam,
                    rekening.naam,
                    BigDecimal(0),
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetPeilDatum = peilDatum.toString(),
                    budgetBetaling = budgetBetaling,
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    betaaldBinnenBudget = budgetOpPeilDatum.min(budgetBetaling),
                    minderDanBudget = minderDanRekening,
                    meerDanBudget = meerDanRekening,
                    meerDanMaandBudget = meerDanMaandRekening,
                    restMaandBudget =
                        if (isVasteLastenRekeningBetaald) BigDecimal(0)
                        else BigDecimal(0).max(budgetMaandBedrag - budgetBetaling - minderDanRekening),
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
        return if (rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN) -bedrag else bedrag
    }

    fun berekenMaandRekening(rekening: Rekening, gekozenPeriode: Periode): BigDecimal {
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

//    fun berekenRekeningSamenvatting(
//        periode: Periode,
//        peilDatum: LocalDate,
//        Rekeningen: List<RekeningDTO>,
//        aflossing: Aflossing.AflossingDTO?
//    ): RekeningSamenvattingDTO {
//        logger.info("berekenRekeningSamenvatting ${Rekeningen.joinToString { it.RekeningNaam }}")
//        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
//        val periodeVoorbij = peilDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
//        val percentagePeriodeVoorbij = 100 * periodeVoorbij / periodeLengte
//        val isPeriodeVoorbij = peilDatum >= periode.periodeEindDatum
//        logger.info("periodeLengte: $periodeLengte, periodeVoorbij: $periodeVoorbij, percentagePeriodeVoorbij: $percentagePeriodeVoorbij")
//        val RekeningMaandInkomsten = Rekeningen
//            .filter {
//                it.rekeningSoort?.uppercase() == RekeningGroep.RekeningGroepSoort.INKOMSTEN.toString() ||
//                        it.rekeningSoort?.uppercase() == RekeningGroep.RekeningGroepSoort.RENTE.toString()
//            }
//            .fold(BigDecimal(0)) { acc, Rekening -> acc + (Rekening.RekeningMaandBedrag ?: BigDecimal(0)) }
//        val werkelijkeMaandInkomsten = Rekeningen
//            .filter {
//                it.rekeningSoort?.uppercase() == RekeningGroep.RekeningGroepSoort.INKOMSTEN.toString() ||
//                        it.rekeningSoort?.uppercase() == RekeningGroep.RekeningGroepSoort.RENTE.toString()
//            }
//            .fold(BigDecimal(0)) { acc, Rekening -> acc + (Rekening.RekeningBetaling ?: BigDecimal(0)) }
//        val RekeningMaandInkomstenBedrag = RekeningMaandInkomsten.max(werkelijkeMaandInkomsten)
//
//        val RekeningBesteedTotPeilDatum = Rekeningen
//            .filter { it.rekeningSoort?.uppercase() == RekeningGroep.RekeningGroepSoort.UITGAVEN.toString() }
//            .fold(BigDecimal(0)) { acc, Rekening -> acc + (Rekening.RekeningBetaling ?: BigDecimal(0)) }
//        val aflossingBesteedTotPeildatum = (aflossing?.aflossingBetaling ?: BigDecimal(0))
//        val besteedTotPeilDatum = RekeningBesteedTotPeilDatum + aflossingBesteedTotPeildatum
//
//        val RekeningNodigNaPeilDatum = if (isPeriodeVoorbij) BigDecimal(0) else {
//            Rekeningen
//                .filter { it.rekeningSoort?.uppercase() == RekeningGroep.RekeningGroepSoort.UITGAVEN.toString() }
//                .fold(BigDecimal(0)) { acc, Rekening ->
//                    val restMaandRekening = if (Rekening.RekeningType == "CONTINU")
//                        (Rekening.RekeningMaandBedrag ?: BigDecimal(0)) - (Rekening.betaaldBinnenRekening ?: BigDecimal(
//                            0
//                        ))
//                    else (Rekening.restMaandRekening ?: BigDecimal(0)) + (Rekening.minderDanRekening ?: BigDecimal(0))
//                    logger.info(">>> RekeningNodigNaPeilDatum: ${Rekening.RekeningNaam} $restMaandRekening")
//                    acc + restMaandRekening
//                }
//        }
//        val aflossingNodigNaPeildatum = if (isPeriodeVoorbij) BigDecimal(0) else
//            (BigDecimal(aflossing?.aflossingsBedrag ?: "0") - (aflossing?.betaaldBinnenAflossing ?: BigDecimal(0)))
//        val nogNodigNaPeilDatum = RekeningNodigNaPeilDatum + aflossingNodigNaPeildatum
//        logger.info(
//            "aflossingNodigNaPeildatum: $aflossingNodigNaPeildatum, RekeningNodigNaPeilDatum: $RekeningNodigNaPeilDatum"
//        )
//
//        val actueleBuffer = RekeningMaandInkomstenBedrag - besteedTotPeilDatum - nogNodigNaPeilDatum
//        return RekeningSamenvattingDTO(
//            percentagePeriodeVoorbij = percentagePeriodeVoorbij,
//            RekeningMaandInkomstenBedrag = if (isPeriodeVoorbij) werkelijkeMaandInkomsten else RekeningMaandInkomsten.max(
//                werkelijkeMaandInkomsten
//            ),
//            besteedTotPeilDatum = besteedTotPeilDatum,
//            nogNodigNaPeilDatum = nogNodigNaPeilDatum,
//            actueleBuffer = actueleBuffer
//        )
//    }

    fun RekeningIsGeldigInPeriode(Rekening: Rekening, periode: Periode): Boolean {
        return (Rekening.vanPeriode == null || periode.periodeStartDatum >= Rekening.vanPeriode.periodeStartDatum) &&
                (Rekening.totEnMetPeriode == null || periode.periodeEindDatum <= Rekening.totEnMetPeriode.periodeEindDatum)
    }
}

