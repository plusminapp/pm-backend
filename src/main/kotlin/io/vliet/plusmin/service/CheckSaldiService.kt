package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.BetalingDTO
import io.vliet.plusmin.domain.Betaling.Companion.reserveringBetalingsSoorten
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CheckSaldiService {
    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var betalingService: BetalingService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Scheduled(cron = "0 1 2 * * *")
    fun nachtelijkeCheck() {

    }

    fun checkSpaarSaldi(gebruiker: Gebruiker): String {
        val saldi = standInPeriodeService.berekenSaldiOpDatum(gebruiker, LocalDate.now())

        val spaarrekeningSaldo = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARREKENING }
            .sumOf { it.openingsBalansSaldo + it.betaling }
        val spaarpotSaldo = saldi
            .filter { it.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.SPAARPOT }
            .sumOf { it.openingsReserveSaldo - it.openingsOpgenomenSaldo + it.reservering - it.opgenomenSaldo - it.betaling }

        if (spaarrekeningSaldo != spaarpotSaldo) {
            logger.warn("SpaarrekeningSaldo ($spaarrekeningSaldo) komt niet overeen met spaarpotSaldo ($spaarpotSaldo) voor ${gebruiker.bijnaam}")
            return "SpaarrekeningSaldo ($spaarrekeningSaldo) komt niet overeen met spaarpotSaldo ($spaarpotSaldo) voor ${gebruiker.bijnaam}"
        } else {
            return "SpaarrekeningSaldo ($spaarrekeningSaldo) komt overeen met spaarpotSaldo ($spaarpotSaldo) voor ${gebruiker.bijnaam}"
        }
    }
}