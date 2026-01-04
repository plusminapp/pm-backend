package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.plus

@Service
class StandMutatiesTussenDatumsService {
    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenMutatieLijstTussenDatums(
        administratie: Administratie,
        vanDatum: LocalDate,
        totEnMetDatum: LocalDate
    ): List<Saldo> {
        val rekeningGroepLijst = rekeningGroepRepository.findRekeningGroepenVoorAdministratie(administratie)
        val betalingen = betalingRepository.findAllByAdministratieTussenDatums(administratie, vanDatum, totEnMetDatum)
        val saldoLijst = rekeningGroepLijst.flatMap { rekeningGroep ->
            rekeningGroep.rekeningen.map { rekening ->
                val periodeBetaling =
                    betalingen
                        .fold(BigDecimal.ZERO) { acc, betaling ->
                            acc + berekenBetalingMutaties(betaling, rekening)
                        }
                val periodeReservering =
                    betalingen.fold(BigDecimal.ZERO) { acc, betaling ->
                        acc + berekenReserveringMutaties(betaling, rekening)
                    }

                // TODO achterstand berekenen
                Saldo(
                    0,
                    rekening,
                    periodeBetaling = periodeBetaling,
                    periodeReservering = periodeReservering,
                    periode = Periode(0, administratie, vanDatum, totEnMetDatum)
                )
            }
        }
        logger.debug("berekenMutatieLijstTussenDatums van $vanDatum tot $totEnMetDatum #betalingen: ${betalingen.size}: ${saldoLijst.joinToString { "${it.rekening.naam} -> B ${it.periodeBetaling} + R ${it.periodeReservering}" }}")
        return saldoLijst
    }

    fun berekenBetalingMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return (if (betaling.bron?.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO) +
                (if (betaling.bestemming?.id == rekening.id) betaling.bedrag else BigDecimal.ZERO)
    }

    fun berekenReserveringMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return (if (betaling.reserveringBron?.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO) +
                (if (betaling.reserveringBestemming?.id == rekening.id) betaling.bedrag else BigDecimal.ZERO)
    }
}
