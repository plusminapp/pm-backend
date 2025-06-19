package io.vliet.plusmin.service

import io.vliet.plusmin.controller.StandController
import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.geslotenPeriodes
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class StandService {
    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    /*
    Algoritme:
    - bepaal de periode voor de peildatum
    - resultaatOpDatum =
        - als de periode gesloten is, haal de stand op uit de database en geef die terug
        - anders (SaldoInPeriodeService):
            - haal de laatste gesloten of opgeruimde periode op
            - bereken de saldi bij de periode opening
            - haal de mutaties tussen begin van de periode en de peildatum
            - bereken de saldi op basis van de opening en de mutaties
            - geef het resultaat terug
        - aggregeer het resltaat per rekeningGroepNaam
        - bereken de resultaatSamenvatting
        - return de StandDTO met alle gegevens
     */
    fun getStandOpDatum(
        gebruiker: Gebruiker,
        peilDatum: LocalDate,
    ): StandController.StandDTO {
        val periode = periodeService.getPeriode(gebruiker, peilDatum)
        return getStandOpPeriode(gebruiker, peilDatum, periode)
    }

    fun getStandOpPeriode(
        gebruiker: Gebruiker,
        peilDatum: LocalDate,
        periode: Periode
    ): StandController.StandDTO {
        val standOpDatum =
            if (geslotenPeriodes.contains(periode.periodeStatus)) {
                saldoRepository
                    .findAllByPeriode(periode)
                    .map { it.toDTO() }
            } else {
                standInPeriodeService
                    .berekenStandInPeriode(periode.gebruiker, peilDatum, periode)
            }
        logger.info("resultaatOpDatum: ${standOpDatum.joinToString { "${it.rekeningGroepNaam} -> ${it.budgetBetaling}" }}")
        val geaggregeerdeStandOpDatum = standOpDatum
            .groupBy { it.rekeningGroepNaam }
            .mapValues { it.value.reduce { acc, budgetDTO -> add(acc, budgetDTO) } }
            .values.toList()
        val standSamenvattingOpDatumDTO =
            berekenResultaatSamenvatting(
                periode,
                peilDatum,
                geaggregeerdeStandOpDatum,
            )
        return StandController.StandDTO(
            datumLaatsteBetaling = betalingRepository.findLaatsteBetalingDatumBijGebruiker(gebruiker),
            periodeStartDatum = periode.periodeStartDatum,
            peilDatum = peilDatum,
            resultaatOpDatum = standOpDatum,
            resultaatSamenvattingOpDatum = standSamenvattingOpDatumDTO,
            geaggregeerdResultaatOpDatum = geaggregeerdeStandOpDatum,
        )
    }

    fun add(saldoDTO1: Saldo.SaldoDTO, saldoDTO2: Saldo.SaldoDTO): Saldo.SaldoDTO {
        return Saldo.SaldoDTO(
            id = 0,
            rekeningGroepNaam = saldoDTO1.rekeningGroepNaam,
            rekeningGroepSoort = saldoDTO1.rekeningGroepSoort,
            budgetType = saldoDTO1.budgetType,
            rekeningNaam = "",
            sortOrder = saldoDTO1.sortOrder,
            openingsSaldo = saldoDTO1.openingsSaldo.plus(saldoDTO2.openingsSaldo),
            achterstandNu = saldoDTO1.achterstandNu?.plus(saldoDTO2.achterstandNu ?: BigDecimal(0)),
            budgetMaandBedrag = saldoDTO1.budgetMaandBedrag.plus(saldoDTO2.budgetMaandBedrag),
            budgetBetaling = saldoDTO1.budgetBetaling.plus(saldoDTO2.budgetBetaling),
            budgetPeilDatum = saldoDTO1.budgetPeilDatum ?: saldoDTO2.budgetPeilDatum,
            budgetOpPeilDatum = saldoDTO1.budgetOpPeilDatum?.plus(saldoDTO2.budgetOpPeilDatum ?: BigDecimal(0)),
            betaaldBinnenBudget = saldoDTO1.betaaldBinnenBudget?.plus(saldoDTO2.betaaldBinnenBudget ?: BigDecimal(0)),
            minderDanBudget = saldoDTO1.minderDanBudget?.plus(saldoDTO2.minderDanBudget ?: BigDecimal(0)),
            meerDanBudget = saldoDTO1.meerDanBudget?.plus(saldoDTO2.meerDanBudget ?: BigDecimal(0)),
            meerDanMaandBudget = saldoDTO1.meerDanMaandBudget?.plus(saldoDTO2.meerDanMaandBudget ?: BigDecimal(0)),
            restMaandBudget = saldoDTO1.restMaandBudget?.plus(saldoDTO2.restMaandBudget ?: BigDecimal(0))
        )
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
}
