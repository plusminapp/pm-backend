package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Rekening.RekeningDTO
import io.vliet.plusmin.domain.RekeningGroep.RekeningGroepSoort
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class RekeningService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var budgetService: BudgetService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(gebruiker: Gebruiker, rekeningenLijst: List<RekeningDTO>): List<RekeningDTO> {
        return rekeningenLijst.map { rekeningDTO -> save(gebruiker, rekeningDTO) }
    }

    fun save(gebruiker: Gebruiker, rekeningDTO: RekeningDTO): RekeningDTO {
        val rekeningGroep = rekeningGroepRepository
            .findRekeningGroepVoorGebruiker(gebruiker, rekeningDTO.rekeningGroep.naam)
            .getOrNull() ?: rekeningGroepRepository.save(RekeningGroep(
            naam = rekeningDTO.rekeningGroep.naam,
            gebruiker = gebruiker,
            rekeningGroepSoort = enumValueOf<RekeningGroepSoort>(rekeningDTO.rekeningGroep.rekeningGroepSoort),
            rekeningGroepIcoonNaam = rekeningDTO.rekeningGroep.rekeningGroepIcoonNaam,
            sortOrder = rekeningDTO.rekeningGroep.sortOrder,
            budgetType = enumValueOf<RekeningGroep.BudgetType>(rekeningDTO.rekeningGroep.budgetType),
            rekeningen = emptyList(),
        ))
        val rekeningOpt = rekeningRepository.findRekeningOpGroepEnNaam(rekeningGroep, rekeningDTO.naam)
            .getOrNull()
        val rekening = if (rekeningOpt != null) {
            logger.info("Rekening bestaat al: ${rekeningOpt.naam} met id ${rekeningOpt.id} voor ${gebruiker.bijnaam}")
            rekeningRepository.save(
                rekeningOpt.fullCopy(
                    rekeningGroep = rekeningGroep,
                    sortOrder = rekeningDTO.sortOrder
                )
            )
        } else {
            rekeningRepository.save(
                Rekening(
                    naam = rekeningDTO.naam,
                    rekeningGroep = rekeningGroep,
                    sortOrder = rekeningDTO.sortOrder,
                    budgetBetaalDag = rekeningDTO.budgetBetaalDag,
                    budgetBedrag = rekeningDTO.budgetBedrag
                )
            )
        }
        logger.info("Opslaan rekening ${rekening.naam} voor ${gebruiker.bijnaam}")
        if (rekeningOpt == null) {
            val periode = periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
//            TODO
//            saldoRepository.save(Saldo(
//                rekening = rekening,
//                bedrag = rekeningDTO.saldo,
//                periode = periode
//            ))
        }
        return rekening.toDTO()
    }

//    fun filterBudgettenVoorPeriode(rekening: Rekening, periode: Periode): List<Budget> {
//        return rekening.budgetten.filter { budget ->
//            budgetService.budgetIsGeldigInPeriode(budget, periode)
//        }
//    }
//
//    fun findRekeningenVoorGebruikerEnPeriode(gebruiker: Gebruiker, periode: Periode): List<Rekening> {
//        val rekeningenLijst = rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
//        return rekeningenLijst.map { rekening ->
//            rekening.fullCopy(
//                budgetten = filterBudgettenVoorPeriode(rekening, periode)
//            )
//        }
//    }
}