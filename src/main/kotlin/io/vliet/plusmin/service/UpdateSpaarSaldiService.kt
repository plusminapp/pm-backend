package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class UpdateSpaarSaldiService {
    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var demoService: DemoService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Scheduled(cron = "0 1 2 * * *")
    fun nachtelijkeCheck() {

    }

    fun updateSpaarpotSaldo(administratie: Administratie) {
        val saldi = standInPeriodeService.berekenSaldiOpDatum(administratie, administratie.vandaag ?: LocalDate.now())

        val spaarrekeningSaldo = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARREKENING }
            .sumOf { it.openingsBalansSaldo + it.periodeBetaling }
        val spaarpotSaldo = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARPOT }
            .sumOf { it.openingsReserveSaldo - it.openingsOpgenomenSaldo + it.periodeReservering - it.periodeOpgenomenSaldo - it.periodeBetaling }

        if (spaarrekeningSaldo != spaarpotSaldo) {
            updateSpaarpotSaldo(spaarrekeningSaldo - spaarpotSaldo, saldi, administratie)
            logger.warn("SpaarrekeningSaldo ($spaarrekeningSaldo) komt niet overeen met spaarpotSaldo ($spaarpotSaldo) voor ${administratie.naam}")
        }
    }

    fun updateSpaarpotSaldo(correctieBoeking: BigDecimal, saldi: List<Saldo.SaldoDTO>, administratie: Administratie) {
        val spaarRekeningNaam = saldi
            .firstOrNull { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARREKENING }
            ?.rekeningNaam
            ?: throw PM_SpaarRekeningNotFoundException(listOf(administratie.naam))
        val gekoppeldeSpaarPot = rekeningRepository
            .findGekoppeldeRekeningenAdministratieEnNaam(administratie, spaarRekeningNaam)
            .firstOrNull()
            ?: throw PM_RekeningNotLinkedException(listOf(administratie.naam, spaarRekeningNaam))
        val spaarSaldo = saldoRepository.findLaatsteSaldoBijRekening(gekoppeldeSpaarPot.id)
            ?: throw PM_GeenSaldoVoorRekeningException(listOf(gekoppeldeSpaarPot.naam, administratie.naam))
        saldoRepository.save(
            spaarSaldo.fullCopy(
                openingsReserveSaldo = spaarSaldo.openingsReserveSaldo + correctieBoeking
            )
        )
    }
}