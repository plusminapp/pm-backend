package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.domain.Saldo
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PeriodeSluitenService {
    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var saldoService: SaldoService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun sluitPeriode(gebruiker: Gebruiker, periodeId: Long, saldoLijst: List<Saldo.SaldoDTO>) {
        val (openingPeriode, periode) = checkPeriodeSluiten(gebruiker, periodeId)

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
                        budgetBetaling = saldo.budgetBetaling,
                        budgetVariabiliteit = rekening.budgetVariabiliteit,
                        periode = periode
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
        val (openingPeriode, periode) = checkPeriodeSluiten(gebruiker, periodeId)
        val openingsBalans = saldoService.getOpeningSaldi(periode)
        val mutatieLijst = saldoService.berekenMutatieLijstOpDatum(gebruiker, periode.periodeStartDatum, periode.periodeEindDatum)
        return  saldoService
            .berekenSaldiOpDatum(openingsBalans, mutatieLijst)
            .map { saldo ->
                if (RekeningGroep.balansRekeningGroepSoort.contains(saldo.rekening.rekeningGroep.rekeningGroepSoort))
                    saldo.toBalansDTO()
                else
                    saldo.toResultaatDTO()
            }
    }

    fun checkPeriodeSluiten(gebruiker: Gebruiker, periodeId: Long): Pair<Periode, Periode> {
        val periodeLijst = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .sortedBy { it.periodeStartDatum }
        val index = periodeLijst.indexOfFirst { it.id == periodeId }

        if (index <= 0) {
            throw IllegalStateException("Periode ${periodeId} kan niet worden gesloten of bestaat niet voor gebruiker ${gebruiker.bijnaam}")
        }
        if (periodeLijst[index - 1].periodeStatus != Periode.PeriodeStatus.GESLOTEN) {
            throw IllegalStateException("Periode ${periodeId} kan niet worden gesloten, de vorige periode is niet gesloten voor gebruiker ${gebruiker.bijnaam}")
        }
        if (periodeLijst[index].periodeStatus != Periode.PeriodeStatus.OPEN)
            throw IllegalStateException("Periode ${periodeId} kan niet worden gesloten, de periode is niet open voor gebruiker ${gebruiker.bijnaam}")

        return Pair(periodeLijst[index-1],periodeLijst[index])
    }
}