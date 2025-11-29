package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.PM_GeenSaldoVoorRekeningException
import io.vliet.plusmin.domain.PM_PeriodeNietGeslotenException
import io.vliet.plusmin.domain.PM_PeriodeNietLaatstGeslotenException
import io.vliet.plusmin.domain.PM_PeriodeNietOpenException
import io.vliet.plusmin.domain.PM_PeriodeNotFoundException
import io.vliet.plusmin.domain.PM_RekeningNotFoundException
import io.vliet.plusmin.domain.PM_VorigePeriodeNietGeslotenException
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Periode.Companion.openPeriodes
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
    lateinit var reserveringService: ReserveringService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun sluitPeriode(administratie: Administratie, periodeId: Long) {
        val (_, periode) = checkPeriodeSluiten(administratie, periodeId)
        val saldiLijst = standInPeriodeService.berekenSaldiOpDatum(periode.periodeEindDatum, periode)
        saldiLijst
            .forEach { saldo ->
                val rekening = rekeningRepository
                    .findRekeningAdministratieEnNaam(administratie, saldo.rekeningNaam)
                    ?: throw PM_RekeningNotFoundException(listOf(saldo.rekeningNaam, administratie.naam))
                saldoRepository.save(
                    Saldo(
                        rekening = rekening,
                        openingsBalansSaldo = saldo.openingsBalansSaldo,
                        openingsReserveSaldo = saldo.openingsReserveSaldo,
                        openingsAchterstand = saldo.openingsAchterstand,
                        budgetMaandBedrag = saldo.budgetMaandBedrag,
                        correctieBoeking = BigDecimal.ZERO,
                        periodeBetaling = saldo.periodeBetaling,
                        periodeReservering = saldo.periodeReservering,
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

    fun checkPeriodeSluiten(administratie: Administratie, periodeId: Long): Pair<Periode, Periode> {
        val periodeLijst = periodeRepository
            .getPeriodesVoorAdministrtatie(administratie)
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
                    administratie.naam
                )
            )
        }
        if (!openPeriodes.contains(periode.periodeStatus))
            throw PM_PeriodeNietOpenException(listOf(periode.id.toString(), administratie.naam))

        return Pair(vorigePeriode, periode)
    }

    fun ruimPeriodeOp(administratie: Administratie, periode: Periode) {
        if (periode.periodeStatus != Periode.PeriodeStatus.GESLOTEN) {
            throw PM_PeriodeNietGeslotenException(listOf(periode.id.toString(), "opgeruimd", administratie.naam))
        }
        betalingRepository.deleteAllByAdministratieTotEnMetDatum(administratie, periode.periodeEindDatum)
        val periodeLijst = periodeRepository
            .getPeriodesVoorAdministrtatie(administratie)
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
    fun heropenPeriode(administratie: Administratie, periode: Periode) {
        if (periode.periodeStatus != Periode.PeriodeStatus.GESLOTEN) {
            throw PM_PeriodeNietGeslotenException(listOf(periode.id.toString(), "heropend", administratie.naam))
        }
        val laatstGeslotenOfOpgeruimdePeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(administratie)
        if (laatstGeslotenOfOpgeruimdePeriode.id != periode.id) {
            throw PM_PeriodeNietLaatstGeslotenException(
                listOf(
                    periode.id.toString(),
                    laatstGeslotenOfOpgeruimdePeriode.id.toString(),
                    administratie.naam
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
        administratie: Administratie,
        periodeId: Long,
        nieuweOpeningsSaldi: List<Saldo.SaldoDTO>
    ): List<Saldo.SaldoDTO> {
        // LET OP:
        // - de alleen gesloten periodes hebben opgeslagen saldi
        // - het aanpassen van de opening van een periode kan alleen bij een OPEN periode waarbij de vorige periode gesloten is
        // om de opening aan te passen worden de correctieboekingen daarom in de opgeslagen Saldo's van die vorige (gesloten!) periode aangepast
        val (vorigePeriode, _) = checkPeriodeSluiten(administratie, periodeId)
        val vorigePeriodeSaldi = saldoRepository.findAllByPeriode(vorigePeriode)
        val aangepasteOpeningsSaldi = nieuweOpeningsSaldi.map { nieuweOpeningsBalansSaldo ->
            val vorigePeriodeSaldo =
                vorigePeriodeSaldi.firstOrNull { it.rekening.naam == nieuweOpeningsBalansSaldo.rekeningNaam }
                    ?: throw PM_GeenSaldoVoorRekeningException(
                        listOf(
                            nieuweOpeningsBalansSaldo.rekeningNaam,
                            administratie.naam
                        )
                    )
            // Update de correctieBoeking
            val correctieboeking =
                nieuweOpeningsBalansSaldo.openingsBalansSaldo - (vorigePeriodeSaldo.openingsBalansSaldo + vorigePeriodeSaldo.periodeBetaling)
            logger.info("wijzigPeriodeOpening: rekening ${nieuweOpeningsBalansSaldo.rekeningNaam}: openingsbalans wordt aangepast van ${vorigePeriodeSaldo.openingsBalansSaldo + vorigePeriodeSaldo.periodeBetaling} naar ${nieuweOpeningsBalansSaldo.openingsBalansSaldo}; correctieboeking = $correctieboeking")
            saldoRepository.save(
                vorigePeriodeSaldo.fullCopy(
                    correctieBoeking = correctieboeking,
                )
            ).toDTO()
        }
        reserveringService.updateOpeningsReserveringsSaldo(administratie)
        updateSpaarSaldiService.updateSpaarpotSaldo(administratie)
        return aangepasteOpeningsSaldi
    }
}
