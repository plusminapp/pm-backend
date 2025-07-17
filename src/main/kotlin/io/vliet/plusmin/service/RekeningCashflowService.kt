package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Rekening.BudgetPeriodiciteit
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMethodeRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class RekeningCashflowService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getCashflowVoorHulpvrager(
        hulpvrager: Gebruiker,
        periode: Periode,
    ): List<CashFlow> {
        logger.info("GET RekeningCashflowService.getCashflowVoorHulpvrager voor ${hulpvrager.email} in periode ${periode.periodeStartDatum}")
        val betalingenInPeriode = betalingRepository.findAllByGebruikerTussenDatums(
            hulpvrager,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        )
        val laatsteBetaling =
            betalingRepository.findDatumLaatsteBetalingBijGebruiker(hulpvrager) ?: periode.periodeStartDatum
        val rekeningGroepen = rekeningService.findRekeningGroepenMetGeldigeRekeningen(hulpvrager, periode)
        val periodeLengte = periode.periodeEindDatum
            .toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val continueBudgetUitgaven = -rekeningGroepen
            .flatMap { it.rekeningen }
            .filter { it.rekeningGroep.budgetType == RekeningGroep.BudgetType.CONTINU }
            .filter { it.budgetBedrag != null }
            .sumOf {
                if (it.budgetPeriodiciteit == BudgetPeriodiciteit.MAAND)
                    it.budgetBedrag?.divide(BigDecimal(periodeLengte), 2, java.math.RoundingMode.HALF_UP)
                        ?: BigDecimal.ZERO
                else
                    it.budgetBedrag?.divide(BigDecimal(7), 2, java.math.RoundingMode.HALF_UP) ?: BigDecimal.ZERO
            }
        val cashflow = periode.periodeStartDatum
            .datesUntil(periode.periodeEindDatum.plusDays(1))
            .toList()
            .fold(Pair(BigDecimal.ZERO, emptyList<CashFlow>())) { (accSaldo, accList), date ->
                val inkomsten =
                    if (date > laatsteBetaling) budgetInkomsten(rekeningGroepen, date)
                    else inkomsten(betalingenInPeriode, date)
                val uitgaven =
                    if (date > laatsteBetaling) continueBudgetUitgaven + vasteBudgetUitgaven(rekeningGroepen, date)
                    else uitgaven(betalingenInPeriode, date)
                val nieuwSaldo =
                    if (date <= laatsteBetaling) accSaldo + inkomsten + uitgaven
                    else null
                val nieuwePrognose =
                    if (date >= laatsteBetaling) accSaldo + inkomsten + uitgaven
                    else null
                val cashFlow = CashFlow(date, inkomsten, uitgaven, nieuwSaldo, nieuwePrognose)
                Pair(
                    (nieuwSaldo ?: BigDecimal(0)).plus(nieuwePrognose ?: BigDecimal(0)),
                    accList + cashFlow
                )
            }.second
        return cashflow.toList()
    }

    fun vasteBudgetUitgaven(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return -rekeningGroepen.flatMap { it.rekeningen }
            .filter {
                it.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST &&
                        it.budgetBetaalDag == date.dayOfMonth
            }
            .sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
    }

    fun budgetInkomsten(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return rekeningGroepen.flatMap { it.rekeningen }
            .filter {
                it.rekeningGroep.budgetType == RekeningGroep.BudgetType.INKOMSTEN &&
                        it.budgetBetaalDag == date.dayOfMonth
            }
            .sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
    }

    fun uitgaven(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingen
            .filter { it.boekingsdatum == date }
            .filter { betaalMethodeRekeningGroepSoort.contains(it.bron.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.bedrag }
    }

    fun inkomsten(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return betalingen
            .filter { it.boekingsdatum == date }
            .filter { betaalMethodeRekeningGroepSoort.contains(it.bestemming.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.bedrag }
    }
}