package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
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
        gebruiker: Gebruiker,
        peilDatum: LocalDate,
        periode: Periode,
        inclusiefOngeldigeRekeningen: Boolean? = false
    ): List<Saldo.SaldoDTO> {

        val laatstGeslotenOfOpgeruimdePeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val openingsSaldi =
            berekenBeginSaldiVanPeriode(laatstGeslotenOfOpgeruimdePeriode, periode, gebruiker).map { it.toDTO() }
        logger.info(
            "openingsSaldi: ${
                openingsSaldi
                    .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING }
                    .joinToString { "${it.rekeningNaam} -> ${it.openingsSaldo}, ${it.achterstand}, ${it.achterstandNu}" }
            }"
        )

        val rekeningenLijst = rekeningGroepRepository
            .findRekeningGroepenVoorGebruiker(gebruiker)
            .flatMap { it.rekeningen }
            .filter { rekening -> inclusiefOngeldigeRekeningen ?: false || rekening.rekeningIsGeldigInPeriode(periode) }

        return rekeningenLijst
            .sortedBy { it.sortOrder }
            .map { rekening ->
                // negatief voor uitgaven, positief voor inkomsten
                val budgetBetaling = getBetalingVoorRekeningInPeriode(rekening, periode)
                val saldo = openingsSaldi.find { it.rekeningNaam == rekening.naam }
                val isRekeningVasteLastOfAflossing = rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST
                val budgetBetaalDagInPeriode = if (rekening.budgetBetaalDag != null)
                    periodeService.berekenDagInPeriode(rekening.budgetBetaalDag, periode)
                else null
                val wordtDezeMaandBetalingVerwacht =
                    rekening.maanden.isNullOrEmpty() || rekening.maanden!!.contains(budgetBetaalDagInPeriode?.monthValue)
                val isBedragBinnenVariabiliteit = if (rekening.budgetBedrag == null) true else {
                    budgetBetaling.abs() <= rekening.toDTO(periode).budgetMaandBedrag?.times(
                        BigDecimal(
                            100 + (rekening.budgetVariabiliteit ?: 0)
                        ).divide(BigDecimal(100))
                    ) &&
                            budgetBetaling.abs() >= rekening.toDTO(periode).budgetMaandBedrag?.times(
                        BigDecimal(
                            100 - (rekening.budgetVariabiliteit ?: 0)
                        ).divide(BigDecimal(100))
                    )
                }
                val achterstand = saldo?.achterstand ?: BigDecimal.ZERO
                // VasteLastenRekening is Betaald als de rekening een vaste lasten rekening is, en óf geen betaling wordt verwacht, óf de betaling binnen de budgetVariabiliteit valt
                val isVasteLastOfAflossingBetaald =
                    isRekeningVasteLastOfAflossing && (!wordtDezeMaandBetalingVerwacht || isBedragBinnenVariabiliteit)
                val budgetMaandBedrag =
                    if (rekening.budgetBedrag == null || !wordtDezeMaandBetalingVerwacht) BigDecimal.ZERO
                    else if (isBedragBinnenVariabiliteit) budgetBetaling.abs()
                    else berekenBudgetMaandBedrag(rekening, periode)
                val budgetOpPeilDatum =
                    if (rekening.budgetBedrag == null) BigDecimal.ZERO
                    else if (!wordtDezeMaandBetalingVerwacht) achterstand.abs()
                    else achterstand.abs() +
                            (berekenBudgetOpPeildatum(
                                rekening,
                                periode,
                                budgetBetaalDagInPeriode,
                                peilDatum
                            ) ?: BigDecimal.ZERO)
                val achterstandNu =
                    if (rekening.budgetBedrag == null || (achterstand.compareTo(BigDecimal.ZERO) == 0)) BigDecimal.ZERO
                    else (achterstand - budgetBetaling).min(BigDecimal.ZERO)
                val betaaldBinnenBudget = (achterstand.abs() + budgetOpPeilDatum).min(budgetBetaling.abs())
                val meerDanMaandBudget =
                    if (rekening.budgetBedrag == null || isVasteLastOfAflossingBetaald) BigDecimal.ZERO
                    else BigDecimal.ZERO.max(budgetBetaling.abs() - budgetMaandBedrag + achterstand)
                val minderDanBudget =
                    if (rekening.budgetBedrag == null || isVasteLastOfAflossingBetaald) BigDecimal.ZERO
                    else BigDecimal.ZERO.max(budgetOpPeilDatum
                        .minus(budgetBetaling.abs())
                        .minus(achterstandNu.abs()))
                val meerDanBudget =
                    if (rekening.budgetBedrag == null || isVasteLastOfAflossingBetaald) BigDecimal.ZERO
                    else BigDecimal.ZERO.max(budgetBetaling.abs() - budgetOpPeilDatum - meerDanMaandBudget)
                Saldo.SaldoDTO(
                    0,
                    rekening.rekeningGroep.naam,
                    rekening.rekeningGroep.rekeningGroepSoort,
                    rekening.rekeningGroep.budgetType,
                    rekening.naam,
                    aflossing = rekening.aflossing?.toDTO(),
                    rekening.rekeningGroep.sortOrder * 1000 + rekening.sortOrder,
                    saldo?.openingsSaldo ?: BigDecimal.ZERO,
                    achterstand = achterstand,
                    achterstandNu = achterstandNu,
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetBetaalDag = rekening.budgetBetaalDag,
                    budgetPeilDatum = peilDatum.toString(),
                    budgetBetaling = budgetBetaling,
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    eerderDanBudget =
                        if (budgetBetaalDagInPeriode != null && !peilDatum.isAfter(budgetBetaalDagInPeriode))
                            budgetMaandBedrag.min(budgetBetaling.abs()) else BigDecimal.ZERO,
                    betaaldBinnenBudget = betaaldBinnenBudget,
                    minderDanBudget = minderDanBudget,
                    meerDanBudget = meerDanBudget,
                    meerDanMaandBudget = meerDanMaandBudget,
                    restMaandBudget =
                        if (isVasteLastOfAflossingBetaald) BigDecimal.ZERO
                        else BigDecimal.ZERO.max(budgetMaandBedrag - budgetBetaling.abs() - minderDanBudget),
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
        val factor =
            if (RekeningGroep.betaalMethodeRekeningGroepSoort.contains(rekening.rekeningGroep.rekeningGroepSoort))
                BigDecimal(-1) else BigDecimal(1)
        val bedrag =
            filteredBetalingen.fold(BigDecimal.ZERO) { acc, betaling -> if (betaling.bron.id == rekening.id) acc + factor * betaling.bedrag else acc - factor * betaling.bedrag }
//        logger.info("Betaling voor rekening ${rekening.naam} in periode ${periode.periodeStartDatum} tot ${periode.periodeEindDatum}: $bedrag met filteredBetalingen: ${filteredBetalingen.size}")
        return bedrag
    }

    fun berekenBudgetMaandBedrag(rekening: Rekening, gekozenPeriode: Periode): BigDecimal {
        val dagenInPeriode: Long =
            gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        return when (rekening.budgetPeriodiciteit) {
            Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag?.times(BigDecimal(dagenInPeriode))
                ?.div(BigDecimal(7))

            Rekening.BudgetPeriodiciteit.MAAND -> rekening.budgetBedrag
            null -> rekening.budgetBedrag
        }?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
    }

    fun berekenBudgetOpPeildatum(
        rekening: Rekening,
        gekozenPeriode: Periode,
        betaaldagInPeriode: LocalDate?,
        peilDatum: LocalDate
    ): BigDecimal? {
        when (rekening.rekeningGroep.budgetType) {
            RekeningGroep.BudgetType.VAST, RekeningGroep.BudgetType.INKOMSTEN -> {
                if (betaaldagInPeriode == null) {
                    throw IllegalStateException("Geen budgetBetaalDag voor ${rekening.naam} met RekeningType 'VAST' van ${rekening.rekeningGroep.gebruiker.email}")
                }
                return if (peilDatum.isAfter(betaaldagInPeriode)) rekening.budgetBedrag else BigDecimal.ZERO
            }

            RekeningGroep.BudgetType.CONTINU -> {
                if (peilDatum < gekozenPeriode.periodeStartDatum) {
                    return BigDecimal.ZERO
                }
                val dagenInPeriode: Long =
                    gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
                val budgetMaandBedrag = when (rekening.budgetPeriodiciteit) {
                    Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag?.times(BigDecimal(dagenInPeriode))
                        ?.div(BigDecimal(7))

                    Rekening.BudgetPeriodiciteit.MAAND -> rekening.budgetBedrag
                    null -> BigDecimal.ZERO
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

            else -> return BigDecimal.ZERO
        }
    }

    fun berekenBeginSaldiVanPeriode(
        laatstGeslotenOfOpgeruimdePeriode: Periode,
        periode: Periode,
        gebruiker: Gebruiker
    ): List<Saldo> {
        val saldiLaatstGeslotenOfOpgeruimdePeriode = saldoRepository
            .findAllByPeriode(laatstGeslotenOfOpgeruimdePeriode)
        val mutatiesInPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            laatstGeslotenOfOpgeruimdePeriode.periodeEindDatum.plusDays(1),
            periode.periodeStartDatum.minusDays(1)
        )
        val saldiBijOpening =
            berekenSaldiOpDatum(
                saldiLaatstGeslotenOfOpgeruimdePeriode,
                mutatiesInPeriode,
                laatstGeslotenOfOpgeruimdePeriode,
                periode
            )
                .map { it.fullCopy(periode = periode) }
        return saldiBijOpening
    }

    fun berekenMutatieLijstTussenDatums(gebruiker: Gebruiker, vanDatum: LocalDate, totDatum: LocalDate): List<Saldo> {
        val rekeningGroepLijst = rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(gebruiker, vanDatum, totDatum)
        val saldoLijst = rekeningGroepLijst.flatMap { rekeningGroep ->
            rekeningGroep.rekeningen.map { rekening ->
                val mutatie =
                    betalingen.fold(BigDecimal.ZERO) { acc, betaling ->
                        acc + this.berekenMutaties(betaling, rekening)
                    }
                Saldo(0, rekening, budgetBetaling = mutatie)
            }
        }
//        logger.info("mutaties van $vanDatum tot §$totDatum #betalingen: ${betalingen.size}: ${saldoLijst.joinToString { "${it.rekening.naam} -> ${it.budgetBetaling}" }}")
        return saldoLijst
    }

    fun berekenMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.bron.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO +
                if (betaling.bestemming.id == rekening.id) betaling.bedrag else BigDecimal.ZERO
    }

    fun berekenSaldiOpDatum(
        periodeSaldi: List<Saldo>,
        mutatieLijst: List<Saldo>,
        openingPeriode: Periode,
        periode: Periode
    ): List<Saldo> {
        val saldoLijst = periodeSaldi.map { saldo: Saldo ->
            val mutatie = mutatieLijst.find { it.rekening.naam == saldo.rekening.naam }?.budgetBetaling
            val periodes = periodeRepository.getPeriodesTussenDatums(
                saldo.rekening.rekeningGroep.gebruiker,
                openingPeriode.periodeStartDatum,
                periode.periodeStartDatum
            )
            val aantalGeldigePeriodes = periodes
                .count { saldo.rekening.rekeningIsGeldigInPeriode(it) }
            saldo.fullCopy(
                openingsSaldo = saldo.openingsSaldo + saldo.budgetBetaling + (mutatie ?: BigDecimal.ZERO),
                achterstand = if (saldo.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING)
                    saldo.achterstand
                            - (BigDecimal(aantalGeldigePeriodes) * saldo.budgetMaandBedrag)
                            + (mutatie ?: (BigDecimal.ZERO))
                else BigDecimal.ZERO
            )
        }
        return saldoLijst
    }


}

