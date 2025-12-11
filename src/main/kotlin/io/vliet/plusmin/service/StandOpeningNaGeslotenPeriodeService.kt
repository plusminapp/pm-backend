package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.geslotenPeriodes
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.plus

@Service
class StandOpeningNaGeslotenPeriodeService {
    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenOpeningSaldiNaGeslotenPeriode(periode: Periode): List<Saldo> {
        if (!geslotenPeriodes.contains(periode.periodeStatus)) {
            throw PM_PeriodeNietGeslotenException(listOf("vorige", periode.administratie.naam))
        }
        val periodeSaldi = saldoRepository.findAllByPeriode(periode)

        val saldoLijst = periodeSaldi.map { periodeSaldo: Saldo ->
            val openingsBalansSaldo =
                periodeSaldo.openingsBalansSaldo + periodeSaldo.periodeBetaling + periodeSaldo.correctieBoeking
            val openingsReserveSaldo =
                periodeSaldo.openingsReserveSaldo + periodeSaldo.periodeReservering - periodeSaldo.periodeBetaling
            val openingsOpgenomenSaldo =
                periodeSaldo.openingsOpgenomenSaldo + periodeSaldo.periodeOpgenomenSaldo + periodeSaldo.periodeBetaling
            val openingsAchterstand =
                periodeSaldo.openingsAchterstand + periodeSaldo.periodeAchterstand

            periodeSaldo.fullCopy(
                openingsBalansSaldo = openingsBalansSaldo,
                openingsReserveSaldo = openingsReserveSaldo,
                openingsOpgenomenSaldo = openingsOpgenomenSaldo,
                openingsAchterstand = openingsAchterstand,
                periodeBetaling = BigDecimal.ZERO,
                periodeReservering = BigDecimal.ZERO,
                periodeOpgenomenSaldo = BigDecimal.ZERO,
                periodeAchterstand = BigDecimal.ZERO,
                correctieBoeking = BigDecimal.ZERO,
            )
        }
        logger.debug(
            "berekenOpeningSaldiNaGeslotenPeriode eindSaldi: ${periode.periodeStartDatum} ${
                saldoLijst.joinToString
                { "${it.rekening.naam} -> B ${it.openingsBalansSaldo}  R ${it.openingsReserveSaldo}  O ${it.openingsOpgenomenSaldo} C ${it.correctieBoeking}" }
            }"
        )
        return saldoLijst
    }
}
