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


    /**
     * Bereken de stand in de opgegeven periode voor de gebruiker.
     * @param peilDatum de datum waarop de stand wordt bepaald
     * @param peilPeriode de periode waarin de stand wordt berekend
     * @param inclusiefOngeldigeRekeningen of ongeldige rekeningen moeten worden meegenomen in de berekening
     * @return een lijst van Saldo.SaldoDTO objecten met de berekende saldi
     *
     * - Haal laatstGeslotenOfOpgeruimdePeriode op voor de gebruiker
     * - Haal de Saldi op van de laatstGeslotenOfOpgeruimdePeriode (uit de database)
     * - Haal de betalingen op voor de gebruiker tussen het einde van de laatstGeslotenOfOpgeruimdePeriode en het begin van de opgegeven periode
     * - Bereken de openingssaldi van de opgegeven periode op basis van de saldi van de laatstGeslotenOfOpgeruimdePeriode en de betalingen
     * - Haal de betalingen op voor de gebruiker tussen de start van de opgegeven periode en de peildatum
     * - Merge de openingssaldi met de betalingen
     * - Bereken de saldi op de peildatum op basis van de openingssaldi van de opgegeven periode en de betalingen
     *
     */

    fun berekenStandInPeriode(
        peilDatum: LocalDate,
        peilPeriode: Periode,
        inclusiefOngeldigeRekeningen: Boolean = false
    ): List<Saldo.SaldoDTO> {
        val gebruiker = peilPeriode.gebruiker
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val eindSaldiVanBasisPeriode = saldoRepository.findAllByPeriode(basisPeriode)
        val betalingenTussenBasisEnPeilPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            basisPeriode.periodeEindDatum.plusDays(1),
            peilPeriode.periodeStartDatum.minusDays(1)
        )
        logger.info(
            "betalingenTussenBasisEnPeilPeriode: van ${basisPeriode.periodeEindDatum.plusDays(1)} tot: ${
                peilPeriode.periodeStartDatum.minusDays(
                    1
                )
            } ${betalingenTussenBasisEnPeilPeriode.joinToString { it.rekening.naam + ' ' + it.budgetBetaling }}"
        )
        val startSaldiVanPeilPeriode = berekenSaldiOpDatum(
            eindSaldiVanBasisPeriode,
            betalingenTussenBasisEnPeilPeriode,
            basisPeriode,
            peilPeriode
        )
        val betalingenInPeilPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            peilPeriode.periodeStartDatum,
            peilDatum
        )
        logger.info("betalingenInPeilPeriode: van ${peilPeriode.periodeStartDatum} tot: ${peilDatum} ${betalingenInPeilPeriode.joinToString { it.rekening.naam + ' ' + it.budgetBetaling }}")

        val saldiLijst = startSaldiVanPeilPeriode.map { saldo ->
            val budgetBetaling =
                betalingenInPeilPeriode.find { it.rekening.naam == saldo.rekening.naam }?.budgetBetaling
            saldo.fullCopy(budgetBetaling = budgetBetaling ?: BigDecimal.ZERO)
        }

        return saldiLijst
            .sortedBy { it.rekening.sortOrder }
            .filter { inclusiefOngeldigeRekeningen || it.rekening.rekeningIsGeldigInPeriode(peilPeriode) }
            .map { saldo ->
                val rekening = saldo.rekening
                val budgetBetaling = saldo.budgetBetaling
                val achterstand = saldo.achterstand
                val openingsSaldo = saldo.openingsSaldo

                val isRekeningVasteLastOfAflossing = rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST
                val budgetBetaalDagInPeriode = if (rekening.budgetBetaalDag != null)
                    periodeService.berekenDagInPeriode(rekening.budgetBetaalDag, peilPeriode)
                else null
                val wordtDezeMaandBetalingVerwacht =
                    rekening.maanden.isNullOrEmpty() || rekening.maanden!!.contains(budgetBetaalDagInPeriode?.monthValue)
                // VasteLastenRekening is Betaald als de rekening een vaste lasten rekening is, en óf geen betaling wordt verwacht, óf de betaling binnen de budgetVariabiliteit valt
                val budgetMaandBedrag =
                    if (rekening.budgetBedrag == null || !wordtDezeMaandBetalingVerwacht) BigDecimal.ZERO
                    else rekening.toDTO(peilPeriode, budgetBetaling.abs()).budgetMaandBedrag ?: BigDecimal.ZERO
                val isVasteLastOfAflossingBetaald =
                    isRekeningVasteLastOfAflossing && (!wordtDezeMaandBetalingVerwacht || budgetBetaling.abs() == budgetMaandBedrag)
                val budgetOpPeilDatum =
                    if (rekening.budgetBedrag == null) BigDecimal.ZERO
                    else if (!wordtDezeMaandBetalingVerwacht) achterstand.abs()
                    else achterstand.abs() +
                            (berekenBudgetOpPeildatum(
                                rekening,
                                peilPeriode,
                                budgetBetaalDagInPeriode,
                                peilDatum
                            ) ?: BigDecimal.ZERO)
                val achterstandNu =
                    if (rekening.rekeningGroep.rekeningGroepSoort != RekeningGroep.RekeningGroepSoort.AFLOSSING) BigDecimal.ZERO
                    else (achterstand + budgetBetaling.abs()).min(BigDecimal.ZERO)
                if (rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING)
                    logger.info("berekenStandInPeriode: ${rekening.naam} basisPeriode: $basisPeriode, peilPeriode: $peilPeriode, peilDatum: $peilDatum, budgetBetaling: $budgetBetaling, achterstand: $achterstand, budgetMaandBedrag: $budgetMaandBedrag, budgetOpPeilDatum: $budgetOpPeilDatum, achterstandNu: $achterstandNu")
                val betaaldBinnenBudget = (achterstand.abs() + budgetOpPeilDatum).min(budgetBetaling.abs())
                val meerDanMaandBudget =
                    if (rekening.budgetBedrag == null || isVasteLastOfAflossingBetaald) BigDecimal.ZERO
                    else BigDecimal.ZERO.max(budgetBetaling.abs() - budgetMaandBedrag + achterstand)
                val minderDanBudget =
                    if (rekening.budgetBedrag == null || isVasteLastOfAflossingBetaald) BigDecimal.ZERO
                    else BigDecimal.ZERO.max(
                        budgetOpPeilDatum
                            .minus(budgetBetaling.abs())
                            .minus(achterstandNu.abs())
                    )
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
                    openingsSaldo,
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

    fun berekenBudgetMaandBedrag(rekening: Rekening, gekozenPeriode: Periode): BigDecimal {
        val dagenInPeriode: Long =
            gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        return when (rekening.budgetPeriodiciteit) {
            Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag?.times(BigDecimal(dagenInPeriode))
                ?.div(BigDecimal(7))

            Rekening.BudgetPeriodiciteit.MAAND -> {
                val budgetBetaalDagInPeriode = if (rekening.budgetBetaalDag != null)
                    periodeService.berekenDagInPeriode(rekening.budgetBetaalDag, gekozenPeriode)
                else null
                if (rekening.maanden.isNullOrEmpty() || rekening.maanden!!.contains(budgetBetaalDagInPeriode?.monthValue))
                // er wordt een betaling verwacht in deze periode
                    rekening.budgetBedrag
                else BigDecimal.ZERO
            }

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
        val factor =
            if (RekeningGroep.resultaatRekeningGroepSoort.contains(rekening.rekeningGroep.rekeningGroepSoort))
                BigDecimal(-1) else BigDecimal(1)

        return if (betaling.bron.id == rekening.id) -factor * betaling.bedrag else BigDecimal.ZERO +
                if (betaling.bestemming.id == rekening.id) factor * betaling.bedrag else BigDecimal.ZERO
    }

    fun berekenSaldiOpDatum(
        periodeSaldi: List<Saldo>,
        mutatieLijst: List<Saldo>,
        basisPeriode: Periode,
        peilPeriode: Periode
    ): List<Saldo> {

        val startDatum = basisPeriode.periodeEindDatum.plusDays(1)
        val eindDatum = peilPeriode.periodeStartDatum.minusDays(1)

        val saldoLijst = periodeSaldi.map { saldo: Saldo ->
            val budgetBetaling = mutatieLijst
                .filter { it.rekening.naam == saldo.rekening.naam }
                .sumOf { it.budgetBetaling }
            val openingsSaldo =
                if (!resultaatRekeningGroepSoort.contains(saldo.rekening.rekeningGroep.rekeningGroepSoort))
                    saldo.openingsSaldo + (budgetBetaling)
                else BigDecimal.ZERO
            val aantalGeldigePeriodes = periodeRepository.getPeriodesTussenDatums(
                saldo.rekening.rekeningGroep.gebruiker, startDatum, eindDatum
            )
                .count { saldo.rekening.rekeningIsGeldigInPeriode(it) }

            val budgetMaandBedrag = saldo.rekening.toDTO(peilPeriode).budgetMaandBedrag ?: BigDecimal.ZERO
            val achterstand =
                if (saldo.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING)
                    saldo.achterstand -
                            (BigDecimal(aantalGeldigePeriodes) * budgetMaandBedrag) +
                            saldo.budgetBetaling + budgetBetaling
                else BigDecimal.ZERO
            if (saldo.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING)
                logger.info("berekenSaldiOpDatum: ${saldo.rekening.naam} basisPeriodeStartDatum: $basisPeriode,  eindDatum: ${peilPeriode}, achterstand eerst: ${saldo.achterstand} achterstand nu: ${achterstand} aantalGeldigePeriodes: $aantalGeldigePeriodes, budgetMaandBedrag: ${budgetMaandBedrag} budgetBetaling: ${budgetBetaling}")
            saldo.fullCopy(
                openingsSaldo = openingsSaldo,
                achterstand = achterstand,
            )
        }
        return saldoLijst
    }
}
