package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Budget.BudgetDTO
import io.vliet.plusmin.domain.Budget.BudgetSamenvattingDTO
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
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "${budgetDTO.rekeningNaam} niet gevonden.")
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

    fun add(budget1: BudgetDTO, budget2: BudgetDTO): BudgetDTO {
        return budget1.fullCopy(
            budgetNaam = budget1.rekeningNaam,
            budgetMaandBedrag = budget1.budgetMaandBedrag?.plus(budget2.budgetMaandBedrag ?: BigDecimal(0)),
            budgetOpPeilDatum = budget1.budgetOpPeilDatum?.plus(budget2.budgetOpPeilDatum ?: BigDecimal(0)),
            budgetBetaling = budget1.budgetBetaling?.plus(budget2.budgetBetaling ?: BigDecimal(0)),
            betaaldBinnenBudget = budget1.betaaldBinnenBudget?.plus(budget2.betaaldBinnenBudget ?: BigDecimal(0)),
            minderDanBudget = budget1.minderDanBudget?.plus(budget2.minderDanBudget ?: BigDecimal(0)),
            meerDanBudget = budget1.meerDanBudget?.plus(budget2.meerDanBudget ?: BigDecimal(0)),
            meerDanMaandBudget = budget1.meerDanMaandBudget?.plus(budget2.meerDanMaandBudget ?: BigDecimal(0)),
            restMaandBudget = budget1.restMaandBudget?.plus(budget2.restMaandBudget ?: BigDecimal(0))
        )
    }

    fun berekenBudgettenOpDatum(gebruiker: Gebruiker, peilDatum: LocalDate): List<BudgetDTO> {
        val saldoPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val gekozenPeriode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, peilDatum) ?: run {
            logger.error("Geen periode voor ${gebruiker.bijnaam} op ${peilDatum}, gebruik ${saldoPeriode.periodeStartDatum}")
            saldoPeriode
        }
        val budgettenLijst = budgetRepository
            .findBudgettenByGebruiker(gebruiker)
            .filter { budget ->
                budgetIsGeldigInPeriode(budget, gekozenPeriode)
            }
        logger.info("budgettenLijst: ${budgettenLijst.joinToString { it.budgetNaam +'/'+ it.vanPeriode?.periodeStartDatum.toString() +'/'+ it.totEnMetPeriode?.periodeStartDatum.toString() }} voor periodeStartDatum ${gekozenPeriode.periodeStartDatum} ")
        return budgettenLijst
            .sortedBy { it.rekening.sortOrder }
            .map { budget ->
                val budgetMaandBedrag = berekenMaandBudget(budget, gekozenPeriode)
                val budgetBetaling = getBetalingVoorBudgetInPeriode(budget, gekozenPeriode)
                val budgetOpPeilDatum = berekenBudgetOpPeildatum(budget, gekozenPeriode, peilDatum)
                val meerDanMaandBudget = BigDecimal(0).max(budgetBetaling - budgetMaandBedrag)
                val minderDanBudget = BigDecimal(0).max(budgetOpPeilDatum - budgetBetaling)
                val meerDanBudget = BigDecimal(0).max(budgetBetaling - budgetOpPeilDatum - meerDanMaandBudget)
                budget.toDTO(
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetPeilDatum = peilDatum.toString(),
                    budgetBetaling = budgetBetaling,
                    budgetOpPeilDatum = budgetOpPeilDatum,
                    betaaldBinnenBudget = budgetOpPeilDatum.min(budgetBetaling),
                    minderDanBudget = minderDanBudget,
                    meerDanBudget = meerDanBudget,
                    meerDanMaandBudget = meerDanMaandBudget,
                    restMaandBudget = BigDecimal(0).max(budgetMaandBedrag - budgetBetaling - minderDanBudget),
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
        return if (budget.rekening.rekeningSoort == Rekening.RekeningSoort.UITGAVEN) -bedrag else bedrag
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
            logger.info(
                "budget ${budget.budgetNaam} van ${gekozenPeriode.periodeStartDatum} tot " +
                        "$peilDatum met maandBudget $maandBudget: $dagenTotPeilDatum/$dagenInPeriode = " +
                        "${(maandBudget * BigDecimal(dagenTotPeilDatum) / BigDecimal(dagenInPeriode))}"
            )
            return (maandBudget * BigDecimal(dagenTotPeilDatum) / BigDecimal(dagenInPeriode))
        } else {
            throw IllegalStateException("Budget ${budget.budgetNaam} heeft geen budgetType")
        }
    }

    fun berekenBudgetSamenvatting(
        periode: Periode,
        peilDatum: LocalDate,
        budgetten: List<BudgetDTO>,
        aflossing: Aflossing.AflossingDTO?
    ): BudgetSamenvattingDTO {
        logger.info("berekenBudgetSamenvatting ${budgetten.joinToString { it.budgetNaam }}")
        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val periodeVoorbij = peilDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val percentagePeriodeVoorbij = 100 * periodeVoorbij / periodeLengte
        val isPeriodeVoorbij = peilDatum >= periode.periodeEindDatum
        logger.info("periodeLengte: $periodeLengte, periodeVoorbij: $periodeVoorbij, percentagePeriodeVoorbij: $percentagePeriodeVoorbij")
        val budgetMaandInkomsten = budgetten
            .filter {
                it.rekeningSoort?.uppercase() == Rekening.RekeningSoort.INKOMSTEN.toString() ||
                        it.rekeningSoort?.uppercase() == Rekening.RekeningSoort.RENTE.toString()
            }
            .fold(BigDecimal(0)) { acc, budget -> acc + (budget.budgetMaandBedrag ?: BigDecimal(0)) }
        val werkelijkeMaandInkomsten = budgetten
            .filter {
                it.rekeningSoort?.uppercase() == Rekening.RekeningSoort.INKOMSTEN.toString() ||
                        it.rekeningSoort?.uppercase() == Rekening.RekeningSoort.RENTE.toString()
            }
            .fold(BigDecimal(0)) { acc, budget -> acc + (budget.budgetBetaling ?: BigDecimal(0)) }
        val budgetMaandInkomstenBedrag = budgetMaandInkomsten.max(werkelijkeMaandInkomsten)

        val budgetBesteedTotPeilDatum = budgetten
            .filter { it.rekeningSoort?.uppercase() == Rekening.RekeningSoort.UITGAVEN.toString() }
            .fold(BigDecimal(0)) { acc, budget -> acc + (budget.budgetBetaling ?: BigDecimal(0)) }
        val aflossingBesteedTotPeildatum = (aflossing?.aflossingBetaling ?: BigDecimal(0))
        val besteedTotPeilDatum = budgetBesteedTotPeilDatum + aflossingBesteedTotPeildatum

        val budgetNodigNaPeilDatum = if (isPeriodeVoorbij) BigDecimal(0) else {
            budgetten
                .filter { it.rekeningSoort?.uppercase() == Rekening.RekeningSoort.UITGAVEN.toString() }
                .fold(BigDecimal(0)) { acc, budget ->
                    val restMaandBudget = if (budget.budgetType == "CONTINU")
                        (budget.budgetMaandBedrag ?: BigDecimal(0)) - (budget.betaaldBinnenBudget ?: BigDecimal(0))
                    else (budget.restMaandBudget ?: BigDecimal(0)) + (budget.minderDanBudget ?: BigDecimal(0))
                    logger.info(">>> budgetNodigNaPeilDatum: ${budget.budgetNaam} $restMaandBudget")
                    acc + restMaandBudget
                }
        }
        val aflossingNodigNaPeildatum = if (isPeriodeVoorbij) BigDecimal(0) else
            (BigDecimal(aflossing?.aflossingsBedrag ?: "0") - (aflossing?.betaaldBinnenAflossing ?: BigDecimal(0)))
        val nogNodigNaPeilDatum = budgetNodigNaPeilDatum + aflossingNodigNaPeildatum
        logger.info(
            "aflossingNodigNaPeildatum: $aflossingNodigNaPeildatum, budgetNodigNaPeilDatum: $budgetNodigNaPeilDatum"
        )

        val actueleBuffer = budgetMaandInkomstenBedrag - besteedTotPeilDatum - nogNodigNaPeilDatum
        return BudgetSamenvattingDTO(
            percentagePeriodeVoorbij = percentagePeriodeVoorbij,
            budgetMaandInkomstenBedrag = if (isPeriodeVoorbij) werkelijkeMaandInkomsten else budgetMaandInkomsten.max(
                werkelijkeMaandInkomsten
            ),
            besteedTotPeilDatum = besteedTotPeilDatum,
            nogNodigNaPeilDatum = nogNodigNaPeilDatum,
            actueleBuffer = actueleBuffer
        )
    }

    fun budgetIsGeldigInPeriode(budget: Budget, periode: Periode): Boolean {
        return (budget.vanPeriode == null || periode.periodeStartDatum >= budget.vanPeriode.periodeStartDatum) &&
                (budget.totEnMetPeriode == null || periode.periodeEindDatum <= budget.totEnMetPeriode.periodeEindDatum)
    }
}

