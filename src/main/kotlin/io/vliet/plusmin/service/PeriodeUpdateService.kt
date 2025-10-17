package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.PM_GeenSaldoVoorRekeningException
import io.vliet.plusmin.domain.PM_PeriodeNietGeslotenException
import io.vliet.plusmin.domain.PM_PeriodeNietLaatstGeslotenException
import io.vliet.plusmin.domain.PM_PeriodeNietOpenException
import io.vliet.plusmin.domain.PM_PeriodeNotFoundException
import io.vliet.plusmin.domain.PM_RekeningNotFoundException
import io.vliet.plusmin.domain.PM_VorigePeriodeNietGeslotenException
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Periode.Companion.openPeriodes
import io.vliet.plusmin.domain.RekeningGroep.Companion.balansRekeningGroepSoort
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
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var updateSpaarSaldiService: UpdateSpaarSaldiService

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var startSaldiVanPeriodeService: StartSaldiVanPeriodeService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun sluitPeriode(gebruiker: Gebruiker, periodeId: Long, saldoLijst: List<Saldo.SaldoDTO>) {
        val (basisPeriode, periode) = checkPeriodeSluiten(gebruiker, periodeId)
        if (saldoLijst.isEmpty()) {
            val eindSaldiVanVorigeGeslotenPeriode = saldoRepository.findAllByPeriode(basisPeriode)
            val betalingenGedurendePeilPeriode = startSaldiVanPeriodeService.berekenMutatieLijstTussenDatums(
                gebruiker,
                periode.periodeStartDatum,
                periode.periodeEindDatum
            )
            logger.info(
                "sluitperiode: eindSaldiVanVorigeGeslotenPeriode: ${
                    eindSaldiVanVorigeGeslotenPeriode
                        .filter { it.rekening.naam == "Greenchoice" }
                        .joinToString { it.rekening.naam + " | OS: " + it.openingsBalansSaldo + " | A: " + it.achterstand + " | BMB: " + it.budgetMaandBedrag + " | BBt: " + it.betaling }
                }"
            )
            val nieuweSaldiLijst = eindSaldiVanVorigeGeslotenPeriode.map { saldo ->
                val betaling = betalingenGedurendePeilPeriode
                    .filter { it.rekening.naam == saldo.rekening.naam }
                    .sumOf { it.betaling }
                val budgetMaandBedrag =
                    if (saldo.rekening.budgetBedrag == null) BigDecimal.ZERO
                    else saldo.rekening.toDTO(periode, betaling).budgetMaandBedrag ?: BigDecimal.ZERO
                val openingsBalansSaldo =
                    if (balansRekeningGroepSoort.contains(saldo.rekening.rekeningGroep.rekeningGroepSoort))
                        saldo.openingsBalansSaldo + saldo.betaling + saldo.correctieBoeking
                    else BigDecimal.ZERO
                val achterstand = BigDecimal.ZERO
//                    if (saldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
//                        (saldo.betaling - saldo.budgetMaandBedrag - saldo.achterstand.abs()).min(BigDecimal.ZERO)
//                    else BigDecimal.ZERO

                saldo.fullCopy(
                    openingsBalansSaldo = openingsBalansSaldo,
                    achterstand = achterstand,
                    budgetMaandBedrag = budgetMaandBedrag,
                    betaling = betaling,
                    correctieBoeking = BigDecimal.ZERO,
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
                    ?: throw PM_RekeningNotFoundException(listOf(saldo.rekeningNaam, gebruiker.bijnaam))
                saldoRepository.save(
                    Saldo(
                        rekening = rekening,
                        openingsBalansSaldo = saldo.openingsBalansSaldo,
                        achterstand = saldo.achterstand,
                        budgetMaandBedrag = saldo.budgetMaandBedrag,
                        correctieBoeking = BigDecimal.ZERO,
                        betaling = saldo.betaling,
                        budgetVariabiliteit = rekening.budgetVariabiliteit,
                        periode = periode,
                    )
                )
            }
        periodeRepository.save(
            periode.fullCopy(
                periodeStatus = Periode.PeriodeStatus.GESLOTEN
            )
        )
    }

    fun voorstelPeriodeSluiten(gebruiker: Gebruiker, periodeId: Long): List<Saldo.SaldoDTO> {
        val (_, periode) = checkPeriodeSluiten(gebruiker, periodeId)
        return standInPeriodeService
            .berekenSaldiInPeriode(periode.periodeEindDatum, periode, true)
    }

    fun checkPeriodeSluiten(gebruiker: Gebruiker, periodeId: Long): Pair<Periode, Periode> {
        val periodeLijst = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .sortedBy { it.periodeStartDatum }
        val index = periodeLijst.indexOfFirst { it.id == periodeId }

        if (index <= 0 || periodeLijst.size < 2) {
            throw PM_PeriodeNotFoundException(listOf(periodeId.toString()))
        }
        val periode = periodeLijst[index]
        val vorigePeriode = periodeLijst[index - 1]

        if (openPeriodes.contains(vorigePeriode.periodeStatus)) {
            throw PM_VorigePeriodeNietGeslotenException(
                listOf(
                    periode.id.toString(),
                    vorigePeriode.id.toString(),
                    gebruiker.bijnaam
                )
            )
        }
        if (!openPeriodes.contains(periode.periodeStatus))
            throw PM_PeriodeNietOpenException(listOf(periode.id.toString(), gebruiker.bijnaam))

        return Pair(vorigePeriode, periode)
    }

    fun ruimPeriodeOp(gebruiker: Gebruiker, periode: Periode) {
        if (periode.periodeStatus != Periode.PeriodeStatus.GESLOTEN) {
            throw PM_PeriodeNietGeslotenException(listOf(periode.id.toString(), "opgeruimd", gebruiker.bijnaam))
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
            throw PM_PeriodeNietGeslotenException(listOf(periode.id.toString(), "heropend", gebruiker.bijnaam))
        }
        val laatstGeslotenOfOpgeruimdePeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        if (laatstGeslotenOfOpgeruimdePeriode.id != periode.id) {
            throw PM_PeriodeNietLaatstGeslotenException(
                listOf(
                    periode.id.toString(),
                    laatstGeslotenOfOpgeruimdePeriode.id.toString(),
                    gebruiker.bijnaam
                )
            )
        }
        saldoRepository.deleteByPeriode(periode)
        periodeRepository.save(
            periode.fullCopy(
                periodeStatus = Periode.PeriodeStatus.OPEN
            )
        )
    }

    fun wijzigPeriodeOpening(
        gebruiker: Gebruiker,
        periodeId: Long,
        nieuweOpeningsSaldi: List<Saldo.SaldoDTO>
    ): List<Saldo.SaldoDTO> {
        // LET OP:
        // - de alleen gesloten periodes hebben opgeslagen saldi
        // - het aanpassen van de opening van een periode kan alleen bij een OPEN periode waarbij de vorige periode gesloten is
        // om de opening aan te passen worden de correctieboekingen daarom in de opgeslagen Saldo's van die vorige (gesloten!) periode aangepast
        val (vorigePeriode, _) = checkPeriodeSluiten(gebruiker, periodeId)
        val vorigePeriodeSaldi = saldoRepository.findAllByPeriode(vorigePeriode)
        val aangepasteOpeningsSaldi = nieuweOpeningsSaldi.map { nieuweOpeningsBalansSaldo ->
            val vorigePeriodeSaldo = vorigePeriodeSaldi.firstOrNull { it.rekening.naam == nieuweOpeningsBalansSaldo.rekeningNaam }
                ?: throw PM_GeenSaldoVoorRekeningException(
                    listOf(
                        nieuweOpeningsBalansSaldo.rekeningNaam,
                        gebruiker.bijnaam
                    )
                )
            // Update de correctieBoeking
            val correctieboeking = nieuweOpeningsBalansSaldo.openingsBalansSaldo - (vorigePeriodeSaldo.openingsBalansSaldo + vorigePeriodeSaldo.betaling)
            logger.info("wijzigPeriodeOpening: rekening ${nieuweOpeningsBalansSaldo.rekeningNaam}: openingsbalans wordt aangepast van ${vorigePeriodeSaldo.openingsBalansSaldo+vorigePeriodeSaldo.betaling} naar ${nieuweOpeningsBalansSaldo.openingsBalansSaldo}; correctieboeking = $correctieboeking")
            saldoRepository.save(
                vorigePeriodeSaldo.fullCopy(
                    correctieBoeking = correctieboeking,
                )
            ).toDTO()
        }
        startSaldiVanPeriodeService.updateOpeningsReserveringsSaldo(gebruiker)
        updateSpaarSaldiService.checkSpaarSaldi(gebruiker)
        return aangepasteOpeningsSaldi
    }
}
