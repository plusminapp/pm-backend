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
import java.math.RoundingMode
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
    lateinit var reserveringService: ReserveringService

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
                    it.budgetBedrag?.divide(BigDecimal(periodeLengte), 2, RoundingMode.HALF_UP)
                        ?: BigDecimal.ZERO
                else
                    it.budgetBedrag?.divide(BigDecimal(7), 2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
            }
        // "betaalde vaste lasten tot date (negatieve waarde)" + "verwachte vaste lasten tot date (negatieve waarde)"
        val vasteLastenAflossingAchterstand = if (periode.periodeStartDatum < laatsteBetaling)
            periode.periodeStartDatum
                .datesUntil(laatsteBetaling.plusDays(1))
                .toList()
                .fold(BigDecimal.ZERO) { accSaldo, date ->
                    val betaaldeVasteLaten = -vasteLastenUitgaven(betalingenInPeriode, date)
                    val verwachteUitgaven = vasteBudgetUitgaven(rekeningGroepen, date)
                    logger.info("Vaste lasten uitgaven op $date: $betaaldeVasteLaten, verwachte uitgaven: $verwachteUitgaven, saldo: $accSaldo")
                    accSaldo + betaaldeVasteLaten + verwachteUitgaven
                } else BigDecimal.ZERO
        logger.info("Vaste lasten aflossing achterstand: $vasteLastenAflossingAchterstand")

        val openingsReserveringsSaldo = openingsReserveringsSaldo(rekeningGroepen, periode)
        val cashflow = periode.periodeStartDatum
            .datesUntil(periode.periodeEindDatum.plusDays(1))
            .toList()
            .fold(Pair(openingsReserveringsSaldo, emptyList<CashFlow>())) { (accSaldo, accList), date ->
                val inkomsten =
                    if (date > laatsteBetaling) budgetInkomsten(rekeningGroepen, date)
                    else inkomsten(betalingenInPeriode, date)
                val uitgaven =
                    if (date > laatsteBetaling.plusDays(1)) continueBudgetUitgaven + vasteBudgetUitgaven(
                        rekeningGroepen,
                        date
                    )
                    else if (date == laatsteBetaling.plusDays(1))
                        continueBudgetUitgaven + vasteBudgetUitgaven(
                            rekeningGroepen,
                            date
                        ) + vasteLastenAflossingAchterstand
                    else uitgaven(betalingenInPeriode, date)
                val saldo = accSaldo + inkomsten + uitgaven
                val nieuwSaldo =
                    if (date <= laatsteBetaling) saldo else null
                val nieuwePrognose =
                    if (date >= laatsteBetaling) saldo else null
                val cashFlow = CashFlow(date, inkomsten, uitgaven, nieuwSaldo, nieuwePrognose)
                Pair(saldo, accList + cashFlow)
            }.second
        return cashflow.toList()
    }

    fun vasteBudgetUitgaven(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return -rekeningGroepen
            .filter { it.budgetType == RekeningGroep.BudgetType.VAST }
            .flatMap { it.rekeningen }
            .filter { it.budgetBetaalDag == date.dayOfMonth }
            .filter { it.maanden.isNullOrEmpty() || it.maanden!!.contains(date.monthValue) }
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

    fun vasteLastenUitgaven(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingen
            .filter { it.boekingsdatum == date }
            .filter { it.bestemming.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST }
            .sumOf { it.bedrag }
    }

    fun openingsReserveringsSaldo(rekeningGroepen: List<RekeningGroep>, periode: Periode): BigDecimal {
        val openingReserveringSaldo: BigDecimal = reserveringService
            .getReserveringenEnBetalingenVoorHulpvrager(periode.gebruiker, periode.periodeStartDatum.minusDays(1))
            .filter {it.key?.rekeningGroep?.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
            .map { it.key!!.id to it.value.first + it.value.second  }
            .toMap().values.sumOf { it }

        val spaarReserveringen = rekeningGroepen
            .filter { it.budgetType == RekeningGroep.BudgetType.SPAREN }
            .flatMap { it.rekeningen }
            .sumOf { it.budgetBedrag ?: BigDecimal.ZERO}
        return openingReserveringSaldo - spaarReserveringen
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