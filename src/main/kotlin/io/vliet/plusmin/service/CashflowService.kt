package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.berekenDagInPeriode
import io.vliet.plusmin.domain.Rekening.BudgetPeriodiciteit
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMethodeRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.ReserveringRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class CashflowService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var startSaldiVanPeriodeService: StartSaldiVanPeriodeService

    @Autowired
    lateinit var reserveringRepository: ReserveringRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getCashflow(
        hulpvrager: Gebruiker,
        periode: Periode,
        metInkomsten: Boolean? = false,
    ): List<CashFlow> {
        logger.info("GET RekeningCashflowService.getCashflowVoorHulpvrager voor ${hulpvrager.email} in periode ${periode.periodeStartDatum}")
        val betalingenInPeriode = betalingRepository
            .findAllByGebruikerTussenDatums(hulpvrager, periode.periodeStartDatum, periode.periodeEindDatum)
            .filter {
                it.bron.rekeningGroep.budgetType !== RekeningGroep.BudgetType.SPAREN &&
                        it.bestemming.rekeningGroep.budgetType !== RekeningGroep.BudgetType.SPAREN
            }
        val spaarReserveringenInPeriode = reserveringRepository
            .findAllByGebruikerTussenDatums(hulpvrager, periode.periodeStartDatum, periode.periodeEindDatum)
            .filter {
                it.bron.rekeningGroep.budgetType === RekeningGroep.BudgetType.SPAREN ||
                        it.bestemming.rekeningGroep.budgetType === RekeningGroep.BudgetType.SPAREN
            }
        val laatsteBetalingDatum =
            betalingRepository.findDatumLaatsteBetalingBijGebruiker(hulpvrager) ?: periode.periodeStartDatum
        val rekeningGroepen = rekeningService.findRekeningGroepenMetGeldigeRekeningen(hulpvrager, periode)
        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val continueBudgetUitgaven = budgetContinueUitgaven(rekeningGroepen, periodeLengte)
        // "betaalde vaste lasten tot date (negatieve waarde)" + "verwachte vaste lasten tot date (negatieve waarde)"
        val vasteLastenAflossingAchterstand = if (periode.periodeStartDatum < laatsteBetalingDatum)
            periode.periodeStartDatum
                .datesUntil(laatsteBetalingDatum.plusDays(1))
                .toList()
                .fold(BigDecimal.ZERO) { accSaldo, date ->
                    val betaaldeVasteLaten =
                        betaaldeVasteLaten(betalingenInPeriode, date, laatsteBetalingDatum, periode)
                    val verwachteUitgaven = budgetVasteLastenUitgaven(rekeningGroepen, date)
//                    logger.info("Vaste lasten aflossing binnen, $date, $accSaldo, $betaaldeVasteLaten, $verwachteUitgaven, ")
                    accSaldo + verwachteUitgaven - betaaldeVasteLaten
                } else BigDecimal.ZERO
//        logger.info("Vaste lasten aflossing achterstand: $vasteLastenAflossingAchterstand, ")

        val openingsReserveringsSaldo = openingsReserveringsSaldo(periode)
        val initalCashflow = CashFlow(
            datum = periode.periodeStartDatum.minusDays(1),
            inkomsten = BigDecimal.ZERO,
            uitgaven = BigDecimal.ZERO,
            aflossing = BigDecimal.ZERO,
            spaarReserveringen = BigDecimal.ZERO,
            saldo = openingsReserveringsSaldo,
            prognose = openingsReserveringsSaldo,
        )
        val cashflow = periode.periodeStartDatum
            .datesUntil(periode.periodeEindDatum.plusDays(1))
            .toList()
            .fold(Pair(openingsReserveringsSaldo, listOf(initalCashflow))) { (accSaldo, accList), date ->
                val inkomsten =
                    if (date > laatsteBetalingDatum) budgetInkomsten(rekeningGroepen, date)
                    else betaaldeInkomsten(betalingenInPeriode, date)
                val uitgaven =
                    if (date > laatsteBetalingDatum.plusDays(1))
                        continueBudgetUitgaven +
                                budgetVasteLastenUitgaven(rekeningGroepen, date) -
                                eerderBetaaldeVasteLastenUitgaven(betalingenInPeriode, date)
                    else if (date.equals(laatsteBetalingDatum.plusDays(1))) {
                        continueBudgetUitgaven +
                                budgetVasteLastenUitgaven(rekeningGroepen, date) +
                                vasteLastenAflossingAchterstand
                    } else betaaldeUitgaven(betalingenInPeriode, date)
                val aflossing =
                    if (date <= laatsteBetalingDatum) betaaldeAflossing(betalingenInPeriode, date)
                    else budgetAflossing(rekeningGroepen, date)
                val spaarReserveringen = spaarReserveringen(spaarReserveringenInPeriode, date)
                val saldo = accSaldo + uitgaven + aflossing + spaarReserveringen +
                        if (metInkomsten ?: false || date <= laatsteBetalingDatum) inkomsten else BigDecimal.ZERO
                val nieuwSaldo =
                    if (date <= laatsteBetalingDatum) saldo else null
                val nieuwePrognose =
                    if (date >= laatsteBetalingDatum) saldo else null
                val cashFlow =
                    CashFlow(date, inkomsten, uitgaven, aflossing, spaarReserveringen, nieuwSaldo, nieuwePrognose)
                Pair(saldo, accList + cashFlow)
            }.second
        return cashflow.toList()
    }

    fun openingsReserveringsSaldo(periode: Periode): BigDecimal {
        val startSaldiVanPeriode = startSaldiVanPeriodeService
            .berekenStartSaldiVanPeilPeriode(periode)
        val saldoBetaalmiddelen = startSaldiVanPeriode
            .filter { betaalMethodeRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.openingsBalansSaldo }
        val saldoSpaartegoed = startSaldiVanPeriode
            .filter { it.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.SPAREN }
            .sumOf { it.openingsReserveSaldo }
        logger.info(
            "Openings saldo betaalmiddelen: $saldoBetaalmiddelen, " +
                    "openings saldo spaartegoed: $saldoSpaartegoed"
        )
        return saldoBetaalmiddelen - saldoSpaartegoed
    }

    fun budgetInkomsten(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return rekeningGroepen.flatMap { it.rekeningen }
            .filter {
                it.rekeningGroep.budgetType == RekeningGroep.BudgetType.INKOMSTEN &&
                        it.budgetBetaalDag == date.dayOfMonth
            }
            .sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
    }

    fun budgetContinueUitgaven(rekeningGroepen: List<RekeningGroep>, periodeLengte: Long): BigDecimal {
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
        return continueBudgetUitgaven
    }

    fun budgetVasteLastenUitgaven(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return -rekeningGroepen
            .asSequence()
            .filter { it.budgetType == RekeningGroep.BudgetType.VAST }//&& it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.UITGAVEN }
            .flatMap { it.rekeningen }
            .filter { it.budgetBetaalDag == date.dayOfMonth }
            .filter { it.maanden.isNullOrEmpty() || it.maanden!!.contains(date.monthValue) }
            .sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
    }

    fun eerderBetaaldeVasteLastenUitgaven(betaaldeVasteLasten: List<Betaling>, date: LocalDate): BigDecimal {
        return -betaaldeVasteLasten
            .filter { it.bestemming.budgetBetaalDag == date.dayOfMonth } // date is NA de laatstebetaaldatum
            .sumOf { it.bedrag }
    }

    fun budgetAflossing(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return -rekeningGroepen
            .asSequence()
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.AFLOSSING }
            .flatMap { it.rekeningen }
            .filter { it.budgetBetaalDag == date.dayOfMonth }
            .filter { it.maanden.isNullOrEmpty() || it.maanden!!.contains(date.monthValue) }
            .sumOf { it.budgetBedrag ?: BigDecimal.ZERO }
    }

    fun betaaldeInkomsten(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return betalingen
            .filter { it.boekingsdatum.equals(date) }
            .filter { it.bron.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN }
            .sumOf { it.bedrag }
    }

    fun betaaldeUitgaven(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingen
            .filter { it.boekingsdatum.equals(date) }
            .filter { it.bestemming.rekeningGroep.rekeningGroepSoort.equals(RekeningGroep.RekeningGroepSoort.UITGAVEN) }
            .sumOf { it.bedrag }
    }

    fun betaaldeVasteLaten(
        betalingen: List<Betaling>,
        date: LocalDate,
        laatsteBetalingDatum: LocalDate,
        periode: Periode
    ): BigDecimal {
        return -betalingen
            .filter {
                it.bestemming.rekeningGroep.budgetType?.equals(RekeningGroep.BudgetType.VAST) ?: false
            }
            .filter {
                it.boekingsdatum.equals(date) &&
                        periode.berekenDagInPeriode(
                            it.bestemming.budgetBetaalDag ?: (periode.gebruiker.periodeDag - 1)
                        ) <= laatsteBetalingDatum // het had al betaald moeten zijn
            }
            .onEach { logger.info("betaaldeVasteLaten: ${it.bestemming.rekeningGroep.rekeningGroepSoort}, ${it.bestemming.rekeningGroep.budgetType}") }
            .sumOf { it.bedrag }
    }

    fun betaaldeAflossing(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingen
            .filter { it.boekingsdatum.equals(date) }
            .filter { it.bestemming.rekeningGroep.rekeningGroepSoort.equals(RekeningGroep.RekeningGroepSoort.AFLOSSING) }
            .sumOf { it.bedrag }
    }

    fun spaarReserveringen(reserveringen: List<Reservering>, date: LocalDate): BigDecimal {
        return reserveringen
            .filter { it.boekingsdatum.equals(date) }
            .sumOf {
                BigDecimal.ZERO +
                        if (it.bron.rekeningGroep.budgetType == RekeningGroep.BudgetType.SPAREN) it.bedrag
                        else if (it.bestemming.rekeningGroep.budgetType == RekeningGroep.BudgetType.SPAREN) -it.bedrag
                        else BigDecimal.ZERO
            }
    }

    fun getBudgetHorizon(
        hulpvrager: Gebruiker,
        periode: Periode,
    ): LocalDate? {
        val cashflowLijst = getCashflow(hulpvrager, periode, metInkomsten = false)
        val budgetHorizon = cashflowLijst
            .filter { (it.saldo != null && it.saldo > BigDecimal.ZERO) || (it.prognose != null && it.prognose > BigDecimal.ZERO) }
            .maxByOrNull { it.datum }
            ?.datum
        logger.info("Budget horizon voor ${hulpvrager.email} in periode ${periode.periodeStartDatum} is $budgetHorizon")
        return budgetHorizon
    }
}
