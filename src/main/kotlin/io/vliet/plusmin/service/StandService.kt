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
    lateinit var cashflowService: CashflowService

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
        val (reserveringsHorizon, budgetHorizon) = cashflowService.getReserveringEnBudgetHorizon(gebruiker, periode)
        val saldiOpDatum =
            if (geslotenPeriodes.contains(periode.periodeStatus)) {
                saldoRepository
                    .findAllByPeriode(periode)
                    .filter { it.rekening.rekeningIsGeldigInPeriode(periode) }
                    .map { it.toDTO() }
            } else {
                standInPeriodeService.berekenSaldiInPeriode(peilDatum, periode)
            }
        val openingsReservePotjesVoorNuSaldo = saldiOpDatum
            .filter {
                (it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN && it.budgetType != RekeningGroep.BudgetType.SPAREN) ||
                        it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING
            }
            .also { logger.info("openingsReservePotjesVoorNuSaldo: ${it.joinToString { it.rekeningNaam + " | " + it.openingsReserveSaldo }}") }
            .fold(BigDecimal.ZERO) { acc, saldoDTO -> acc + (saldoDTO.openingsReserveSaldo) }
        val geaggregeerdeStandOpDatum = saldiOpDatum
            .groupBy { it.rekeningGroepNaam }
            .mapValues { it.value.reduce { acc, budgetDTO -> fullAdd(acc, budgetDTO) } }
            .values.toList()
        val standSamenvattingOpDatumDTO =
            berekenResultaatSamenvatting(
                periode,
                peilDatum,
                geaggregeerdeStandOpDatum,
                openingsReservePotjesVoorNuSaldo
            )
        return StandController.StandDTO(
            datumLaatsteBetaling = betalingRepository.findDatumLaatsteBetalingBijGebruiker(gebruiker),
            periodeStartDatum = periode.periodeStartDatum,
            peilDatum = peilDatum,
            budgetHorizon = budgetHorizon,
            reserveringsHorizon = reserveringsHorizon,
            resultaatOpDatum = saldiOpDatum,
            resultaatSamenvattingOpDatum = standSamenvattingOpDatumDTO,
            geaggregeerdResultaatOpDatum = geaggregeerdeStandOpDatum,
        )
    }

    fun fullAdd(saldoDTO1: Saldo.SaldoDTO, saldoDTO2: Saldo.SaldoDTO): Saldo.SaldoDTO {
        return Saldo.SaldoDTO(
            id = 0,
            rekeningGroepNaam = saldoDTO1.rekeningGroepNaam,
            rekeningGroepSoort = saldoDTO1.rekeningGroepSoort,
            budgetType = saldoDTO1.budgetType,
            rekeningNaam = "",
            sortOrder = saldoDTO1.sortOrder,
            openingsBalansSaldo = saldoDTO1.openingsBalansSaldo.plus(saldoDTO2.openingsBalansSaldo),
            openingsReserveSaldo = saldoDTO1.openingsReserveSaldo.plus(saldoDTO2.openingsReserveSaldo),
            achterstand = BigDecimal.ZERO,
//                if (saldoDTO1.budgetType == RekeningGroep.BudgetType.VAST)
//                saldoDTO1.achterstand.plus(saldoDTO2.achterstand)
//            else BigDecimal.ZERO,

            achterstandOpPeilDatum = if (saldoDTO1.budgetType == RekeningGroep.BudgetType.VAST)
                (saldoDTO1.achterstandOpPeilDatum ?: BigDecimal.ZERO).plus(
                    saldoDTO2.achterstandOpPeilDatum ?: BigDecimal.ZERO
                )
            else BigDecimal.ZERO,
            budgetMaandBedrag = saldoDTO1.budgetMaandBedrag.plus(saldoDTO2.budgetMaandBedrag),
            betaling = saldoDTO1.betaling.plus(saldoDTO2.betaling),
            reservering = saldoDTO1.reservering.plus(saldoDTO2.reservering),
            budgetPeilDatum = saldoDTO1.budgetPeilDatum ?: saldoDTO2.budgetPeilDatum,
            budgetOpPeilDatum = saldoDTO1.budgetOpPeilDatum?.plus(saldoDTO2.budgetOpPeilDatum ?: BigDecimal.ZERO),
            betaaldBinnenBudget = saldoDTO1.betaaldBinnenBudget?.plus(saldoDTO2.betaaldBinnenBudget ?: BigDecimal.ZERO),
            minderDanBudget = saldoDTO1.minderDanBudget?.plus(saldoDTO2.minderDanBudget ?: BigDecimal.ZERO),
            meerDanBudget = saldoDTO1.meerDanBudget?.plus(saldoDTO2.meerDanBudget ?: BigDecimal.ZERO),
            meerDanMaandBudget = saldoDTO1.meerDanMaandBudget?.plus(saldoDTO2.meerDanMaandBudget ?: BigDecimal.ZERO),
            restMaandBudget = saldoDTO1.restMaandBudget?.plus(saldoDTO2.restMaandBudget ?: BigDecimal.ZERO)
        )
    }

    fun berekenResultaatSamenvatting(
        periode: Periode,
        peilDatum: LocalDate,
        saldi: List<Saldo.SaldoDTO>,
        openingsReservePotjesVoorNuSaldo: BigDecimal = BigDecimal.ZERO
    ): Saldo.ResultaatSamenvattingOpDatumDTO {
//        logger.info("berekenResultaatSamenvatting ${saldi.joinToString { it.toString() }}")
        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val periodeVoorbij = peilDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val isPeriodeVoorbij = peilDatum >= periode.periodeEindDatum
        val percentagePeriodeVoorbij = if (isPeriodeVoorbij) 100 else 100 * periodeVoorbij / periodeLengte
        val budgetMaandInkomsten = saldi
            .filter {
                it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN
            }
            .fold(BigDecimal.ZERO) { acc, saldoDTO -> acc + (saldoDTO.budgetMaandBedrag) }
        val werkelijkeMaandInkomsten = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN }
            .fold(BigDecimal.ZERO) { acc, saldoDTO -> acc - (saldoDTO.betaling) }
        val maandInkomstenBedrag = if (isPeriodeVoorbij) werkelijkeMaandInkomsten else budgetMaandInkomsten
        logger.info("budgetMaandInkomsten: $budgetMaandInkomsten, werkelijkeMaandInkomsten: $werkelijkeMaandInkomsten, maandInkomstenBedrag: $maandInkomstenBedrag")

        val besteedTotPeilDatum = saldi
            .filter {
                it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING ||
                        (it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN &&
                                it.budgetType != RekeningGroep.BudgetType.SPAREN)
            }
            .fold(BigDecimal.ZERO) { acc, saldoDTO -> acc + (saldoDTO.betaling) }

        val maandSpaarBudget = saldi
            .filter {
                it.budgetType == RekeningGroep.BudgetType.SPAREN
            }
            .fold(BigDecimal.ZERO) { acc, saldoDTO -> acc + (saldoDTO.budgetMaandBedrag) }

        val gespaardTotPeilDatum = betalingRepository
            .findSpaarReserveringenInPeriode(
                gebruiker = periode.gebruiker,
                startDatum = periode.periodeStartDatum,
                eindDatum = peilDatum
            )
            .fold(BigDecimal.ZERO) { acc, reservering -> acc + (reservering.bedrag) }
        logger.info("besteedTotPeilDatum: $besteedTotPeilDatum, gespaardTotPeilDatum: $gespaardTotPeilDatum op $peilDatum")

        val nogNodigNaPeilDatum = if (isPeriodeVoorbij) BigDecimal.ZERO else {
            saldi
                .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN || it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING }
                .fold(BigDecimal.ZERO) { acc, saldoDTO ->
                    val restMaandRekening = if (saldoDTO.budgetType == RekeningGroep.BudgetType.CONTINU)
                        (saldoDTO.budgetMaandBedrag) - (saldoDTO.betaaldBinnenBudget ?: BigDecimal.ZERO)
                    else (saldoDTO.restMaandBudget ?: BigDecimal.ZERO) + (saldoDTO.minderDanBudget
                        ?: BigDecimal.ZERO) + (saldoDTO.achterstandOpPeilDatum ?: BigDecimal.ZERO).abs()
                    logger.info("RekeningNodigNaPeilDatum: ${saldoDTO.rekeningGroepNaam} $restMaandRekening")
                    acc + restMaandRekening
                }
        }

        val actueleBuffer =
            maandInkomstenBedrag + openingsReservePotjesVoorNuSaldo - besteedTotPeilDatum - nogNodigNaPeilDatum - gespaardTotPeilDatum.min(
                maandSpaarBudget
            )
//                    if (isPeriodeVoorbij) gespaardTotPeilDatum
//                    else gespaardTotPeilDatum.min(maandSpaarBudget)
        return Saldo.ResultaatSamenvattingOpDatumDTO(
            percentagePeriodeVoorbij = percentagePeriodeVoorbij,
            openingsReservePotjesVoorNuSaldo = openingsReservePotjesVoorNuSaldo,
            budgetMaandInkomstenBedrag =
                if (isPeriodeVoorbij)
                    werkelijkeMaandInkomsten
                else budgetMaandInkomsten.max(werkelijkeMaandInkomsten),
            besteedTotPeilDatum = besteedTotPeilDatum,
            gespaardTotPeilDatum = gespaardTotPeilDatum.min(maandSpaarBudget),
            nogNodigNaPeilDatum = nogNodigNaPeilDatum,
            actueleBuffer = actueleBuffer,
            extraGespaardTotPeilDatum = (gespaardTotPeilDatum - maandSpaarBudget).max(BigDecimal.ZERO)
        )
    }
}
