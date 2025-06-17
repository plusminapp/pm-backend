package io.vliet.plusmin.service

import io.vliet.plusmin.controller.SaldoController
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.RekeningGroep
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
class SaldoGeslotenPeriodeService {
    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var saldoResultaatService: SaldoResultaatService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getStandOpDatum(
        periode: Periode
    ): SaldoController.StandDTO {
        val gebruiker = periode.gebruiker
        val periodeLijst = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .sortedBy { it.periodeStartDatum }
        val index = periodeLijst.indexOfFirst { it.id == periode.id }
        if (index <= 0) {
            throw IllegalStateException("Index: ${index}, periode ${periode.id} is de pseudo-periode of bestaat niet voor gebruiker ${gebruiker.bijnaam}")
        }

        val openingPeriode = periodeLijst[index - 1]
        val resultaatOpDatum = getMutaties(periode)
        val geaggregeerdResultaatOpDatum = resultaatOpDatum
            .groupBy { it.rekeningGroepNaam }
            .mapValues { it.value.reduce { acc, budgetDTO -> add(acc, budgetDTO) } }
            .values.toList()

        val resultaatSamenvattingOpDatumDTO =
            saldoResultaatService.berekenResultaatSamenvatting(
                periode,
                periode.periodeEindDatum,
                geaggregeerdResultaatOpDatum,
            )

        return SaldoController.StandDTO(
            datumLaatsteBetaling = betalingRepository.findLaatsteBetalingDatumBijGebruiker(gebruiker),
            periodeStartDatum = periode.periodeStartDatum,
            peilDatum = periode.periodeEindDatum,
            openingsBalans = getBalans(openingPeriode),
            mutatiesOpDatum = getMutaties(periode),
            balansOpDatum = getBalans(periode),
            resultaatOpDatum = getMutaties(periode),
            resultaatSamenvattingOpDatumDTO = resultaatSamenvattingOpDatumDTO,
            geaggregeerdResultaatOpDatum = geaggregeerdResultaatOpDatum,
        )
    }

    fun getBalans(periode: Periode): List<Saldo.SaldoDTO> {
        val saldi = saldoRepository.findAllByPeriode(periode)
        return saldi
            .filter { RekeningGroep.balansRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .map { it.toBalansDTO() }
    }

    fun getMutaties(periode: Periode): List<Saldo.SaldoDTO> {
        val saldi = saldoRepository.findAllByPeriode(periode)
        return saldi
            .filter { RekeningGroep.resultaatRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .map { it.toResultaatDTO() }
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
//            periode = saldoDTO1.periode,
            budgetPeilDatum = saldoDTO1.budgetPeilDatum ?: saldoDTO2.budgetPeilDatum,
            budgetOpPeilDatum = saldoDTO1.budgetOpPeilDatum?.plus(saldoDTO2.budgetOpPeilDatum ?: BigDecimal(0)),
            betaaldBinnenBudget = saldoDTO1.betaaldBinnenBudget?.plus(saldoDTO2.betaaldBinnenBudget ?: BigDecimal(0)),
            minderDanBudget = saldoDTO1.minderDanBudget?.plus(saldoDTO2.minderDanBudget ?: BigDecimal(0)),
            meerDanBudget = saldoDTO1.meerDanBudget?.plus(saldoDTO2.meerDanBudget ?: BigDecimal(0)),
            meerDanMaandBudget = saldoDTO1.meerDanMaandBudget?.plus(saldoDTO2.meerDanMaandBudget ?: BigDecimal(0)),
            restMaandBudget = saldoDTO1.restMaandBudget?.plus(saldoDTO2.restMaandBudget ?: BigDecimal(0))
        )
    }

}


