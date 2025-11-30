package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Periode.Companion.geslotenPeriodes
import io.vliet.plusmin.domain.RekeningGroep.Companion.balansRekeningGroepSoort
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.plus

@Service
class StandStartVanPeriodeService {
    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var standOpeningNaGeslotenPeriodeService: StandOpeningNaGeslotenPeriodeService

    @Autowired
    lateinit var standMutatiesTussenDatumsService: StandMutatiesTussenDatumsService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenStartSaldiVanPeriode(administratie: Administratie, periodeId: Long): List<Saldo.SaldoDTO> {
        val periode = periodeRepository.findById(periodeId)
            .orElseThrow { PM_PeriodeNotFoundException(listOf(periodeId.toString(), administratie.naam)) }
        if (periode.administratie.id != administratie.id)
            throw PM_PeriodeNotFoundException(listOf(periodeId.toString(), administratie.naam))
        return berekenStartSaldiVanPeriode(periode)
            .filter { balansRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sortedBy { it.rekening.sortOrder }
            .map { it.toDTO() }
    }

    fun berekenStartSaldiVanPeriode(periode: Periode): List<Saldo> {

        val administratie = periode.administratie
        logger.info("berekenStartSaldiVanPeriode: periode: ${periode.periodeStartDatum} voor administratie ${administratie.naam}")

        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(administratie)
        val basisPeriodeEindSaldi = standOpeningNaGeslotenPeriodeService.berekenOpeningSaldiNaGeslotenPeriode(basisPeriode)

        if (basisPeriode.periodeEindDatum.plusDays(1).equals(periode.periodeStartDatum) ) {
            return basisPeriodeEindSaldi
        }

        val betalingenTussenBasisEnPeilPeriode = standMutatiesTussenDatumsService.berekenMutatieLijstTussenDatums(
            administratie,
            basisPeriode.periodeEindDatum.plusDays(1),
            periode.periodeStartDatum.minusDays(1)
        )
        val saldoLijst = basisPeriodeEindSaldi.map { basisPeriodeEindSaldo: Saldo ->
            val betaling = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.periodeBetaling }
            val reservering = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.periodeReservering }
            val opgenomenSaldo = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.periodeOpgenomenSaldo }
            val achterstand = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.periodeAchterstand }

            val openingsBalansSaldo =
                basisPeriodeEindSaldo.openingsBalansSaldo + betaling
            val openingsReserveSaldo =
                basisPeriodeEindSaldo.openingsReserveSaldo + reservering - betaling
            val openingsOpgenomenSaldo =
                basisPeriodeEindSaldo.openingsOpgenomenSaldo + opgenomenSaldo - betaling
            val openingsAchterstand =
                basisPeriodeEindSaldo.openingsAchterstand + achterstand

            basisPeriodeEindSaldo.fullCopy(
                openingsBalansSaldo = openingsBalansSaldo,
                openingsReserveSaldo = openingsReserveSaldo,
                openingsOpgenomenSaldo = openingsOpgenomenSaldo,
                openingsAchterstand = openingsAchterstand,
            )
        }
        logger.info("openingsSaldi: ${periode.periodeStartDatum} ${saldoLijst.joinToString { "${it.rekening.naam} -> B ${it.openingsBalansSaldo}  R ${it.openingsReserveSaldo}  O ${it.openingsOpgenomenSaldo} C ${it.correctieBoeking}" }}")
        return saldoLijst
    }
}
