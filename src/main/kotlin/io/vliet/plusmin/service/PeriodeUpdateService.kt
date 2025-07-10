package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Periode.Companion.openPeriodes
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.domain.RekeningGroep.Companion.balansRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.resultaatRekeningGroepSoort
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PeriodeUpdateService {
    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun sluitPeriode(gebruiker: Gebruiker, periodeId: Long, saldoLijst: List<Saldo.SaldoDTO>) {
        val (basisPeriode, periode) = checkPeriodeSluiten(gebruiker, periodeId)
        if (saldoLijst.isEmpty()) {
            val eindSaldiVanVorigeGeslotenPeriode = saldoRepository.findAllByPeriode(basisPeriode)
            val betalingenGedurendePeilPeriode = standInPeriodeService.berekenMutatieLijstTussenDatums(
                gebruiker,
                periode.periodeStartDatum,
                periode.periodeEindDatum
            )
            logger.info(
                "sluitperiode: eindSaldiVanVorigeGeslotenPeriode: ${
                    eindSaldiVanVorigeGeslotenPeriode
                        .filter { it.rekening.naam == "Greenchoice" }
                        .joinToString { it.rekening.naam + " | OS: " + it.openingsSaldo + " | A: " + it.achterstand + " | BMB: " + it.budgetMaandBedrag + " | BBt: " + it.budgetBetaling }
                }"
            )
            val nieuweSaldiLijst = eindSaldiVanVorigeGeslotenPeriode.map { saldo ->
                val budgetBetaling = betalingenGedurendePeilPeriode
                    .filter { it.rekening.naam == saldo.rekening.naam }
                    .sumOf { it.budgetBetaling }
                val budgetMaandBedrag =
                    if (saldo.rekening.budgetBedrag == null) BigDecimal.ZERO
                    else saldo.rekening.toDTO(periode, budgetBetaling).budgetMaandBedrag ?: BigDecimal.ZERO
                val openingsSaldo =
                    if (balansRekeningGroepSoort.contains(saldo.rekening.rekeningGroep.rekeningGroepSoort))
                        saldo.openingsSaldo + saldo.budgetBetaling
                    else BigDecimal.ZERO
                val achterstand = BigDecimal.ZERO
//                    if (saldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
//                        (saldo.budgetBetaling - saldo.budgetMaandBedrag - saldo.achterstand.abs()).min(BigDecimal.ZERO)
//                    else BigDecimal.ZERO

                saldo.fullCopy(
                    openingsSaldo = openingsSaldo,
                    achterstand = achterstand,
                    budgetMaandBedrag = budgetMaandBedrag,
                    budgetBetaling = budgetBetaling,
                    oorspronkelijkeBudgetBetaling = budgetBetaling,
                    budgetVariabiliteit = saldo.rekening.budgetVariabiliteit,
                    periode = periode
                )
            }
            sluitPeriodeIntern(gebruiker, periode, nieuweSaldiLijst.map { it.toDTO() })
        } else {
            sluitPeriodeIntern(gebruiker, periode, saldoLijst)
        }
    }

    private fun sluitPeriodeIntern(gebruiker: Gebruiker, periode: Periode, saldoLijst: List<Saldo.SaldoDTO>) {
        saldoLijst
            .forEach { saldo ->
                val rekening = rekeningRepository
                    .findRekeningGebruikerEnNaam(gebruiker, saldo.rekeningNaam)
                    ?: throw IllegalStateException("Rekening ${saldo.rekeningNaam} bestaat niet voor gebruiker ${gebruiker.bijnaam}")
                saldoRepository.save(
                    Saldo(
                        rekening = rekening,
                        openingsSaldo = saldo.openingsSaldo,
                        achterstand = saldo.achterstand,
                        budgetMaandBedrag = saldo.budgetMaandBedrag,
                        oorspronkelijkeBudgetBetaling = saldo.budgetBetaling,
                        budgetBetaling = saldo.budgetBetaling,
                        budgetVariabiliteit = rekening.budgetVariabiliteit,
                        periode = periode,
                    )
                )
            }
        val newPeriode =
            periodeRepository.save(
                periode.fullCopy(
                    periodeStatus = Periode.PeriodeStatus.GESLOTEN
                )
            )
    }

    fun voorstelPeriodeSluiten(gebruiker: Gebruiker, periodeId: Long): List<Saldo.SaldoDTO> {
        val (_, periode) = checkPeriodeSluiten(gebruiker, periodeId)
        return standInPeriodeService
            .berekenStandInPeriode(periode.periodeEindDatum, periode, true)
    }

    fun checkPeriodeSluiten(gebruiker: Gebruiker, periodeId: Long): Pair<Periode, Periode> {
        val periodeLijst = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .sortedBy { it.periodeStartDatum }
        val index = periodeLijst.indexOfFirst { it.id == periodeId }

        if (index <= 0) {
            throw IllegalStateException("Periode ${periodeId} kan niet worden gesloten of bestaat niet voor gebruiker ${gebruiker.bijnaam}")
        }
        if (openPeriodes.contains(periodeLijst[index - 1].periodeStatus)) {
            throw IllegalStateException("Periode ${periodeId} kan niet worden gesloten/gewijzigd, de vorige periode ${periodeLijst[index - 1].id} is niet gesloten voor gebruiker ${gebruiker.bijnaam}")
        }
        if (periodeLijst[index].periodeStatus != Periode.PeriodeStatus.OPEN)
            throw IllegalStateException("Periode ${periodeId} kan niet worden gesloten/gewijzigd, de periode is niet open voor gebruiker ${gebruiker.bijnaam}")

        return Pair(periodeLijst[index - 1], periodeLijst[index])
    }

    fun ruimPeriodeOp(gebruiker: Gebruiker, periode: Periode) {
        if (periode.periodeStatus != Periode.PeriodeStatus.GESLOTEN) {
            throw IllegalStateException("Periode ${periode.id} kan niet worden opgeruimd, de periode is niet gesloten voor gebruiker ${gebruiker.bijnaam}")
        }
        betalingRepository.deleteAllByGebruikerTotEnMetDatum(gebruiker, periode.periodeEindDatum)
        val periodeLijst = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .filter { it.periodeStartDatum <= periode.periodeStartDatum }
        periodeLijst.forEach {
            periodeRepository.save(
                it.fullCopy(
                    periodeStatus = Periode.PeriodeStatus.OPGERUIMD
                )
            )
        }
    }

    @org.springframework.transaction.annotation.Transactional
    fun heropenPeriode(gebruiker: Gebruiker, periode: Periode) {
        if (periode.periodeStatus != Periode.PeriodeStatus.GESLOTEN) {
            throw IllegalStateException("Periode ${periode.id} kan niet worden heropend, de periode is niet gesloten voor gebruiker ${gebruiker.bijnaam}")
        }
        val laatstGeslotenOfOpgeruimdePeriode = periodeRepository
            .getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
            ?: throw IllegalStateException("Er is geen laatst gesloten of opgeruimde periode voor gebruiker ${gebruiker.bijnaam}")
        if (laatstGeslotenOfOpgeruimdePeriode.id != periode.id) {
            throw IllegalStateException("Periode ${periode.id} kan niet worden heropend, het is niet de laatst gesloten periode (dat is periode ${laatstGeslotenOfOpgeruimdePeriode.id}) voor gebruiker ${gebruiker.bijnaam}")
        }
        saldoRepository.deleteByPeriode(periode)
        periodeRepository.save(
            periode.fullCopy(
                periodeStatus = Periode.PeriodeStatus.OPEN
            )
        )
    }

    fun wijzigPeriodeOpening(gebruiker: Gebruiker, periodeId: Long, nieuweOpeningsSaldi: List<Saldo.SaldoDTO>) {
        // LET OP: de opening van een periode wordt opgeslagen als sluiting van de vorige periode
        // om de opening aan te passen worden de budgetBetalingen in de opgeslagen Saldo's van die vorige (gesloten!) periode aangepast
        val (vorigePeriode, _) = checkPeriodeSluiten(gebruiker, periodeId)
        val vorigePeriodeSaldi = saldoRepository.findAllByPeriode(vorigePeriode)
        nieuweOpeningsSaldi.forEach { nieuweOpeningsSaldo ->
            val saldo = vorigePeriodeSaldi.firstOrNull { it.rekening.naam == nieuweOpeningsSaldo.rekeningNaam }
            if (saldo == null) {
                logger.warn("wijzigPeriodeOpening: rekening ${nieuweOpeningsSaldo.rekeningNaam} bestaat niet in de (vorige) periode ${vorigePeriode.id} voor gebruiker ${gebruiker.bijnaam}; openingsSaldo wordt NIET aangepast")
            } else {
                // Update de bestaande Saldo met de nieuwe openingsSaldo
                val nieuweBudgetBetaling = nieuweOpeningsSaldo.openingsSaldo - saldo.openingsSaldo
                logger.info("wijzigPeriodeOpening: rekening ${nieuweOpeningsSaldo.rekeningNaam}: budgetBetaling wordt aangepast van ${saldo.budgetBetaling} naar ${nieuweBudgetBetaling}")
                saldoRepository.save(saldo.fullCopy(budgetBetaling = nieuweBudgetBetaling))
            }
        }
    }
}
