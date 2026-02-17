package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.berekenDagInPeriode
import io.vliet.plusmin.domain.Rekening.BudgetPeriodiciteit
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMiddelenRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesVoorNuRekeningGroepSoort
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
class CashflowService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningUtilitiesService: RekeningUtilitiesService

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var standStartVanPeriodeService: StandStartVanPeriodeService

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getCashflow(
        administratie: Administratie,
        startDatum: LocalDate,
        eindDatum: LocalDate,
        metInkomsten: Boolean? = false,
    ): List<CashFlow> {
        val periode = periodeService.getPeriode(administratie, startDatum)
        logger.debug("GET RekeningCashflowService.getCashflowVoorHulpvrager voor ${administratie.naam} in periode ${startDatum}")
        val betalingenInPeriode = betalingRepository
            .findAllByAdministratieTussenDatums(administratie, startDatum, eindDatum)
            .filter {
                it.bron?.rekeningGroep?.budgetType !== RekeningGroep.BudgetType.SPAREN &&
                        it.bestemming?.rekeningGroep?.budgetType !== RekeningGroep.BudgetType.SPAREN
            }
        val laatsteBetalingDatum =
            betalingRepository.findDatumLaatsteBetalingBijAdministratie(administratie) ?: startDatum
        val rekeningGroepen = rekeningUtilitiesService.findRekeningGroepenMetGeldigeRekeningen(administratie, periode)
        val periodeLengte = eindDatum.toEpochDay() - startDatum.toEpochDay() + 1
        val continueBudgetUitgaven = budgetContinueUitgaven(rekeningGroepen, periodeLengte)
        // "betaalde vaste lasten tot date (negatieve waarde)" + "verwachte vaste lasten tot date (negatieve waarde)"
        val vasteLastenAflossingAchterstand = if (startDatum < laatsteBetalingDatum)
            startDatum
                .datesUntil(laatsteBetalingDatum.plusDays(1))
                .toList()
                .fold(BigDecimal.ZERO) { accSaldo, date ->
                    val betaaldeVasteLaten =
                        betaaldeVasteLaten(betalingenInPeriode, date, laatsteBetalingDatum, periode)
                    val verwachteUitgaven = budgetVasteLastenUitgaven(rekeningGroepen, date)
//                    logger.debug("Vaste lasten aflossing binnen, $date, $accSaldo, $betaaldeVasteLaten, $verwachteUitgaven, ")
                    accSaldo + verwachteUitgaven - betaaldeVasteLaten
                } else BigDecimal.ZERO
//        logger.debug("Vaste lasten aflossing achterstand: $vasteLastenAflossingAchterstand, ")

        val openingsReserveringsSaldo = openingsReserveringsSaldo(periode)
        val initalCashflow = CashFlow(
            datum = startDatum.minusDays(1),
            inkomsten = BigDecimal.ZERO,
            uitgaven = BigDecimal.ZERO,
            aflossing = BigDecimal.ZERO,
            spaarReserveringen = BigDecimal.ZERO,
            potjesVoorNuReserveringen = BigDecimal.ZERO,
            saldo = openingsReserveringsSaldo,
            prognose = openingsReserveringsSaldo,
        )
        val cashflow = startDatum
            .datesUntil(eindDatum.plusDays(1))
            .toList()
            .fold(Pair(openingsReserveringsSaldo, listOf(initalCashflow))) { (accSaldo, accList), date ->
                val inkomsten =
                    if (date > laatsteBetalingDatum)
                        (budgetInkomsten(rekeningGroepen, date) - eerderOntvangenInkomsten(betalingenInPeriode, date))
                            .max(BigDecimal.ZERO)
                    else betaaldeInkomsten(betalingenInPeriode, date)
                val uitgaven =
                    if (date > laatsteBetalingDatum.plusDays(1))
                        continueBudgetUitgaven +
                                budgetVasteLastenUitgaven(rekeningGroepen, date) -
                                eerderBetaaldeVasteLastenAflossing(betalingenInPeriode, date)
                    else if (date.equals(laatsteBetalingDatum.plusDays(1))) {
                        continueBudgetUitgaven +
                                budgetVasteLastenUitgaven(rekeningGroepen, date) +
                                vasteLastenAflossingAchterstand
                    } else betaaldeUitgaven(betalingenInPeriode, date)
                val aflossing =
                    if (date <= laatsteBetalingDatum) betaaldeAflossing(betalingenInPeriode, date)
                    else budgetAflossing(rekeningGroepen, date)
                val potjesVoorNuReserveringen = potjesVoorNuReserveringen(betalingenInPeriode, date)
                val spaarReserveringen = spaarReserveringen(betalingenInPeriode, date)
                val saldo = accSaldo + uitgaven + aflossing + spaarReserveringen +
                        if (metInkomsten ?: false || date <= laatsteBetalingDatum) inkomsten else BigDecimal.ZERO
                val nieuwSaldo =
                    if (date <= laatsteBetalingDatum) saldo else null
                val nieuwePrognose =
                    if (date >= laatsteBetalingDatum) saldo else null
                val cashFlow =
                    CashFlow(
                        date,
                        inkomsten,
                        uitgaven,
                        aflossing,
                        potjesVoorNuReserveringen,
                        spaarReserveringen,
                        nieuwSaldo,
                        nieuwePrognose
                    )
                Pair(saldo, accList + cashFlow)
            }.second
        return cashflow.toList()
    }

    fun openingsReserveringsSaldo(periode: Periode): BigDecimal {
        val startSaldiVanPeriode = standStartVanPeriodeService
            .berekenStartSaldiVanPeriode(periode)
        val saldoBetaalmiddelen = startSaldiVanPeriode
            .filter { betaalMiddelenRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.openingsBalansSaldo }
        val saldoSpaarpot = startSaldiVanPeriode
            .filter { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARPOT }
            .sumOf { it.openingsReserveSaldo }
        logger.debug(
            "Openings saldo betaalmiddelen: $saldoBetaalmiddelen, " +
                    "openings saldo spaarpot: $saldoSpaarpot"
        )
        return saldoBetaalmiddelen - saldoSpaarpot
    }

    fun budgetInkomsten(rekeningGroepen: List<RekeningGroep>, date: LocalDate): BigDecimal {
        return rekeningGroepen.flatMap { it.rekeningen }
            .filter {
                it.rekeningGroep.budgetType == RekeningGroep.BudgetType.INKOMSTEN &&
                        it.budgetBetaalDag == date.dayOfMonth &&
                        it.betaalMethoden.all { bm -> betaalMiddelenRekeningGroepSoort.contains(bm.rekeningGroep.rekeningGroepSoort) }
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

    fun eerderBetaaldeVasteLastenAflossing(betalingenInPeriode: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingenInPeriode
            .filter { it.bestemming?.rekeningGroep?.budgetType == RekeningGroep.BudgetType.VAST } // vaste uitgave
            .filter { it.bestemming?.budgetBetaalDag == date.dayOfMonth } // date is NA de laatstebetaaldatum
            .sumOf { it.bedrag }
    }

    fun eerderOntvangenInkomsten(betalingenInPeriode: List<Betaling>, date: LocalDate): BigDecimal {
        return betalingenInPeriode
            .filter { it.bron?.rekeningGroep?.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN } // inkomsten
            .filter {
                it.bron?.betaalMethoden?.all { bm -> betaalMiddelenRekeningGroepSoort.contains(bm.rekeningGroep.rekeningGroepSoort) }!!
            }
            .filter { it.bron?.budgetBetaalDag == date.dayOfMonth } // date is NA de laatstebetaaldatum
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
            .filter { it.bron?.rekeningGroep?.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN }
            .filter {
                it.bron?.betaalMethoden?.all { bm -> betaalMiddelenRekeningGroepSoort.contains(bm.rekeningGroep.rekeningGroepSoort) }!!
            }
            .sumOf { it.bedrag }
    }

    fun betaaldeUitgaven(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingen
            .filter { it.boekingsdatum.equals(date) }
            .filter {
                it.bestemming?.rekeningGroep?.rekeningGroepSoort?.equals(RekeningGroep.RekeningGroepSoort.UITGAVEN)
                    ?: false
            }
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
                it.bestemming?.rekeningGroep?.budgetType?.equals(RekeningGroep.BudgetType.VAST) ?: false
            }
            .filter {
                it.boekingsdatum.equals(date) &&
                        periode.berekenDagInPeriode(
                            it.bestemming?.budgetBetaalDag ?: (periode.administratie.periodeDag - 1)
                        ) <= laatsteBetalingDatum // het had al betaald moeten zijn
            }
            .onEach {
                logger.debug(
                    "betaaldeVasteLaten: {}, {}",
                    it.bestemming?.rekeningGroep?.rekeningGroepSoort,
                    it.bestemming?.rekeningGroep?.budgetType
                )
            }
            .sumOf { it.bedrag }
    }

    fun betaaldeAflossing(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return -betalingen
            .filter { it.boekingsdatum.equals(date) }
            .filter {
                it.bestemming?.rekeningGroep?.rekeningGroepSoort?.equals(RekeningGroep.RekeningGroepSoort.AFLOSSING)
                    ?: false
            }
            .sumOf { it.bedrag }
    }

    fun spaarReserveringen(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return betalingen
            .filter { it.boekingsdatum.equals(date) }
            .sumOf {
                BigDecimal.ZERO +
                        if (it.betalingsSoort == Betaling.BetalingsSoort.RESERVEREN) {
                            if (it.reserveringBron!!.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARPOT) -it.bedrag
                            else if (it.reserveringBestemming!!.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARPOT) it.bedrag
                            else BigDecimal.ZERO
                        } else BigDecimal.ZERO
            }
    }

    fun potjesVoorNuReserveringen(betalingen: List<Betaling>, date: LocalDate): BigDecimal {
        return betalingen
            .filter { it.boekingsdatum.equals(date) }
            .sumOf {
                BigDecimal.ZERO +
                        if (potjesVoorNuRekeningGroepSoort.contains(it.reserveringBron?.rekeningGroep?.rekeningGroepSoort)) it.bedrag
                        else if (potjesVoorNuRekeningGroepSoort.contains(it.reserveringBestemming?.rekeningGroep?.rekeningGroepSoort)) -it.bedrag
                        else BigDecimal.ZERO
            }
    }

    fun getReserveringEnBudgetHorizon(
        hulpvrager: Administratie,
        startDatum: LocalDate,
        eindDatum: LocalDate,
        openingPotjesVoorNuSaldo: BigDecimal = BigDecimal.ZERO,
    ): Pair<LocalDate, LocalDate> {
        val reserveringsHorizon =
            betalingRepository.getReserveringsHorizon(hulpvrager)
                ?: run {
                    logger.warn("Geen reserveringsHorizon gevonden voor ${hulpvrager.naam}")
                    startDatum.minusDays(1)
                }

        val cashflowLijst = getCashflow(hulpvrager, startDatum, eindDatum, metInkomsten = false)
        val budgetHorizon = cashflowLijst
            .filter {
                (it.saldo != null && it.saldo.minus(openingPotjesVoorNuSaldo) > BigDecimal.ZERO) ||
                        (it.prognose != null && it.prognose.minus(openingPotjesVoorNuSaldo) > BigDecimal.ZERO)
            }
            .maxByOrNull { it.datum }
            ?.datum
            ?: run {
                logger.warn("Geen budgetHorizon gevonden voor ${hulpvrager.naam}")
                startDatum.minusDays(1)
            }
        logger.debug("Budget horizon voor ${hulpvrager.naam} in periode ${startDatum} is PVNR ${openingPotjesVoorNuSaldo}, RH $reserveringsHorizon, BH $budgetHorizon")
        return Pair(reserveringsHorizon, budgetHorizon)
    }
}
