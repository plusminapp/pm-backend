package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Budget
import io.vliet.plusmin.domain.Budget.BudgetDTO
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.BudgetRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.webjars.NotFoundException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class BudgetService {
    @Autowired
    lateinit var budgetRepository: BudgetRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(gebruiker: Gebruiker, budgetenLijst: List<BudgetDTO>): List<BudgetDTO> {
        return budgetenLijst.map { budgetDTO -> upsert(gebruiker, budgetDTO) }
    }

    fun upsert(gebruiker: Gebruiker, budgetDTO: BudgetDTO): BudgetDTO {
        val rekening = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, budgetDTO.rekeningNaam)
            ?: throw NotFoundException("${budgetDTO.rekeningNaam} niet gevonden.")
        val budget = budgetRepository.findByRekeningEnBudgetNaam(rekening, budgetDTO.budgetNaam)
        val newBudget = if (budget == null) {
            logger.info("Nieuw budget ${budgetDTO.budgetNaam} voor gebruiker ${gebruiker.email}")
            budgetRepository.save(
                Budget(
                    id = 0,
                    rekening = rekening,
                    budgetNaam = budgetDTO.budgetNaam,
                    budgetPeriodiciteit = Budget.BudgetPeriodiciteit.valueOf(budgetDTO.budgetPeriodiciteit.uppercase()),
                    bedrag = budgetDTO.bedrag,
                    betaalDag = budgetDTO.betaalDag
                )
            )
        } else {
            logger.info("Bestaand budget ${budget.id}/${budget.budgetNaam} voor gebruiker ${gebruiker.email}")
            budgetRepository.save(
                budget.fullCopy(
                    rekening = rekening,
                    budgetNaam = budgetDTO.budgetNaam,
                    budgetPeriodiciteit = Budget.BudgetPeriodiciteit.valueOf(budgetDTO.budgetPeriodiciteit.uppercase()),
                    bedrag = budgetDTO.bedrag,
                    betaalDag = budgetDTO.betaalDag
                )
            )
        }
        return newBudget.toDTO()
    }

    fun berekenBudgettenOpDatum(gebruiker: Gebruiker, peilDatum: LocalDate): List<BudgetDTO> {
        val budgettenLijst = budgetRepository.findBudgettenByGebruiker(gebruiker)
        val saldoPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val gekozenPeriode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, peilDatum) ?: run {
            logger.error("Geen periode voor ${gebruiker.bijnaam} op ${peilDatum}, gebruik ${saldoPeriode.periodeStartDatum}")
            saldoPeriode
        }
        return budgettenLijst
            .sortedBy { it.rekening.sortOrder }
            .map { budget ->
                budget.toDTO(
                    berekenMaandBudget(budget, gekozenPeriode),
                    peilDatum.toString(),
                    berekenBudgetOpPeildatum(budget, gekozenPeriode, peilDatum),
                    getBetalingVoorBudgetInPeriode(budget, gekozenPeriode)
                )
            }
    }

    fun getBetalingVoorBudgetInPeriode(budget: Budget, periode: Periode): BigDecimal {
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(
            budget.rekening.gebruiker,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        )
        val filteredBetalingen = betalingen.filter { it.budget?.id == budget.id }
        val bedrag =
            filteredBetalingen.fold(BigDecimal(0)) { acc, betaling -> if (betaling.bron.id == budget.rekening.id) acc + betaling.bedrag else acc - betaling.bedrag }
        return bedrag
    }

    fun berekenMaandBudget(budget: Budget, gekozenPeriode: Periode): BigDecimal {
        val dagenInPeriode: Long =
            gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        return when (budget.budgetPeriodiciteit) {
            Budget.BudgetPeriodiciteit.WEEK -> budget.bedrag * BigDecimal(dagenInPeriode) / BigDecimal(7)
            Budget.BudgetPeriodiciteit.MAAND -> budget.bedrag
        }.setScale(2, RoundingMode.HALF_UP)
    }

    fun berekenBudgetOpPeildatum(budget: Budget, gekozenPeriode: Periode, peilDatum: LocalDate): BigDecimal {
        if (budget.rekening.budgetType == Rekening.BudgetType.VAST) {
            if (budget.betaalDag == null) {
                throw IllegalStateException("Geen betaaldag voor ${budget.budgetNaam} met budgetType 'VAST' van ${budget.rekening.gebruiker.email}")
            }
            val betaaldagInPeriode =
                if (budget.betaalDag < gekozenPeriode.periodeStartDatum.dayOfMonth) {
                    gekozenPeriode.periodeStartDatum.plusMonths(1).withDayOfMonth(budget.betaalDag)
                } else {
                    gekozenPeriode.periodeStartDatum.withDayOfMonth(budget.betaalDag)
                }
            return if (betaaldagInPeriode.isAfter(peilDatum)) BigDecimal(0) else budget.bedrag
        } else if (budget.rekening.budgetType == Rekening.BudgetType.CONTINU) {
            if (peilDatum < gekozenPeriode.periodeStartDatum) {
                return BigDecimal(0)
            }
            val dagenInPeriode: Long =
                gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
            val maandBudget = when (budget.budgetPeriodiciteit) {
                Budget.BudgetPeriodiciteit.WEEK -> budget.bedrag * BigDecimal(dagenInPeriode) / BigDecimal(7)
                Budget.BudgetPeriodiciteit.MAAND -> budget.bedrag
            }
            if (peilDatum >= gekozenPeriode.periodeEindDatum) {
                return maandBudget
            }
            val dagenTotPeilDatum: Long = peilDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
            logger.info("budget ${budget.budgetNaam} van ${gekozenPeriode.periodeStartDatum} tot " +
                    "$peilDatum met maandBudget $maandBudget: $dagenTotPeilDatum/$dagenInPeriode = " +
                    "${(maandBudget * BigDecimal(dagenTotPeilDatum) / BigDecimal(dagenInPeriode))}")
            return (maandBudget * BigDecimal(dagenTotPeilDatum) / BigDecimal(dagenInPeriode))
        } else {
            throw IllegalStateException("Budget ${budget.budgetNaam} heeft geen budgetType")
        }
    }
}

