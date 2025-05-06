package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Rekening.RekeningDTO
import io.vliet.plusmin.domain.Rekening.RekeningSoort
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RekeningService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

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
        val rekeningOpt = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, rekeningDTO.naam)
        val rekening = if (rekeningOpt != null) {
            logger.info("Rekening bestaat al: ${rekeningOpt.naam} met id ${rekeningOpt.id} voor ${gebruiker.bijnaam}")
            rekeningRepository.save(
                rekeningOpt.fullCopy(
                    rekeningSoort = enumValueOf<RekeningSoort>(rekeningDTO.rekeningSoort),
                    nummer = rekeningDTO.nummer,
                    sortOrder = rekeningDTO.sortOrder
                )
            )
        } else {
            rekeningRepository.save(
                Rekening(
                    gebruiker = gebruiker,
                    rekeningSoort = enumValueOf<RekeningSoort>(rekeningDTO.rekeningSoort),
                    nummer = rekeningDTO.nummer,
                    naam = rekeningDTO.naam,
                    sortOrder = rekeningDTO.sortOrder
                )
            )
        }
        logger.info("Opslaan rekening ${rekening.naam} voor ${gebruiker.bijnaam}")
        if (rekeningOpt == null) {
            val periode = periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
            saldoRepository.save(Saldo(
                rekening = rekening,
                bedrag = rekeningDTO.saldo,
                periode = periode
            ))
        }
        return rekening.toDTO()
    }

    fun filterBudgettenVoorPeriode(rekening: Rekening, periode: Periode): List<Budget> {
        return rekening.budgetten.filter { budget ->
            budgetService.budgetIsGeldigInPeriode(budget, periode)
        }
    }

    fun findRekeningenVoorGebruikerEnPeriode(gebruiker: Gebruiker, periode: Periode): List<Rekening> {
        val rekeningenLijst = rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
        return rekeningenLijst.map { rekening ->
            rekening.fullCopy(
                budgetten = filterBudgettenVoorPeriode(rekening, periode)
            )
        }
    }
}