package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.geslotenPeriodes
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.plus

@Service
class StandEindeVanGeslotenPeriodeService {
    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenEindSaldiVanGeslotenPeriode(administratie: Administratie, periodeId: Long): List<Saldo> {
        val periode = periodeRepository.findById(periodeId)
            .orElseThrow { PM_PeriodeNotFoundException(listOf(periodeId.toString(), administratie.naam)) }
        if (periode.administratie.id != administratie.id)
            throw PM_PeriodeNotFoundException(listOf(periodeId.toString(), administratie.naam))
        return berekenEindSaldiVanGeslotenPeriode(periode)
            .sortedBy { it.rekening.sortOrder }
    }

    fun berekenEindSaldiVanGeslotenPeriode(periode: Periode): List<Saldo> {
        if (!geslotenPeriodes.contains(periode.periodeStatus)) {
            throw PM_PeriodeNietGeslotenException(listOf("vorige", periode.administratie.naam))
        }
        val periodeSaldi = saldoRepository.findAllByPeriode(periode)

        val saldoLijst = periodeSaldi.map { periodeSaldo: Saldo ->
            val openingsBalansSaldo =
                periodeSaldo.openingsBalansSaldo + periodeSaldo.betaling + periodeSaldo.correctieBoeking
            val openingsReserveSaldo =
                periodeSaldo.openingsReserveSaldo + periodeSaldo.reservering - periodeSaldo.betaling
            val openingsOpgenomenSaldo =
                periodeSaldo.openingsOpgenomenSaldo + periodeSaldo.opgenomenSaldo + periodeSaldo.betaling

            periodeSaldo.fullCopy(
                openingsBalansSaldo = openingsBalansSaldo,
                openingsReserveSaldo = openingsReserveSaldo,
                openingsOpgenomenSaldo = openingsOpgenomenSaldo,
                correctieBoeking = periodeSaldo.correctieBoeking,
                achterstand = periodeSaldo.achterstand,
            )
        }
        logger.info(
            "eindSaldi: ${periode.periodeStartDatum} ${
                saldoLijst.joinToString
                { "${it.rekening.naam} -> B ${it.openingsBalansSaldo}  R ${it.openingsReserveSaldo}  O ${it.openingsOpgenomenSaldo} C ${it.correctieBoeking}" }
            }"
        )
        return saldoLijst
    }
}
