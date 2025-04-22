package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Budget
import io.vliet.plusmin.domain.Budget.BudgetDTO
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.BudgetRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
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
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "${budgetDTO.rekeningNaam} niet gevonden.")
        val budget = budgetRepository.findByRekeningEnBudgetNaam(rekening, budgetDTO.budgetNaam)
        val newBudget = if (budget == null) {
            logger.info("Nieuw budget ${budgetDTO.budgetNaam} voor gebruiker ${gebruiker.email}")
            budgetRepository.save(
                Budget(
                    id = 0,
                    rekening = rekening,
                    budgetNaam = budgetDTO.budgetNaam,
                    budgetType = Budget.BudgetType.valueOf(budgetDTO.budgetType.uppercase()),
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
                    budgetType = Budget.BudgetType.valueOf(budgetDTO.budgetType.uppercase()),
                    budgetPeriodiciteit = Budget.BudgetPeriodiciteit.valueOf(budgetDTO.budgetPeriodiciteit.uppercase()),
                    bedrag = budgetDTO.bedrag,
                    betaalDag = budgetDTO.betaalDag
                )
            )
        }
        return newBudget.toDTO()
    }

//    fun berekenBudgetRestVoorPeriode(budget: Budget, periode: Periode, peilDatum: LocalDate): BigDecimal {
//        val betalingenInPeriode = betalingRepository
//            .findAllByGebruikerTussenDatums(budget.rekening.gebruiker, periode.periodeStartDatum, peilDatum)
//            .filter { it.budget?.id == budget.id }
//            .fold(BigDecimal(0)) { acc, betaling -> acc + betaling.bedrag }
//        val dagenInPeriode = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
//        val budgetMaandBedrag = when (budget.budgetPeriodiciteit) {
//            Budget.BudgetPeriodiciteit.WEEK -> BigDecimal(budget.bedrag.longValueExact() * dagenInPeriode / 7)
//            Budget.BudgetPeriodiciteit.MAAND -> budget.bedrag
//        }
//        return budgetMaandBedrag - betalingenInPeriode
//    }

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
                budget.toDTO(peilDatum.toString(), getBetalingVoorBudgetInPeriode(budget, gekozenPeriode))
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
}