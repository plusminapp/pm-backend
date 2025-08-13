package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.balansRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMethodeRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.reserveringRekeningGroepSoort
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.plus

@Service
class StartSaldiVanPeriodeService {
    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var reserveringRepository: ReserveringRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun openingsReserveringsSaldo(periode: Periode): BigDecimal {
        val startSaldiVanPeriode = berekenStartSaldiVanPeilPeriode(periode)
        val saldoBetaalmiddelen = startSaldiVanPeriode
            .filter { betaalMethodeRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.openingsBalansSaldo }
        val saldoSpaartegoed = startSaldiVanPeriode
            .filter { it.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.SPAREN }
            .sumOf { it.openingsReserveSaldo }
        logger.info(
            "Openings saldo betaalmiddelen: $saldoBetaalmiddelen, " +
                    "openings saldo spaartegoed: $saldoSpaartegoed"
        )
        return saldoBetaalmiddelen - saldoSpaartegoed
    }


    fun berekenStartSaldiVanPeilPeriode(peilPeriode: Periode): List<Saldo> {
        val gebruiker = peilPeriode.gebruiker
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)

        val basisPeriodeSaldi = saldoRepository.findAllByPeriode(basisPeriode)
        val betalingenEnReserveringenTussenBasisEnPeilPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            basisPeriode.periodeEindDatum.plusDays(1),
            peilPeriode.periodeStartDatum.minusDays(1)
        )
        val saldoLijst = basisPeriodeSaldi.map { basisPeriodeSaldo: Saldo ->
            val betaling = betalingenEnReserveringenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeSaldo.rekening.naam }
                .sumOf { it.betaling }
            val openingsBalansSaldo =
                if (balansRekeningGroepSoort.contains(basisPeriodeSaldo.rekening.rekeningGroep.rekeningGroepSoort))
                    basisPeriodeSaldo.openingsBalansSaldo + basisPeriodeSaldo.betaling + betaling
                else BigDecimal.ZERO
            val inkomsten = betalingenEnReserveringenTussenBasisEnPeilPeriode
                .filter { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.INKOMSTEN }
                .sumOf { it.betaling }
            val reservering = betalingenEnReserveringenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeSaldo.rekening.naam }
                .sumOf { it.reservering }
            val openingsReserveSaldo =
                if (basisPeriodeSaldo.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER)
                    basisPeriodeSaldo.openingsReserveSaldo + basisPeriodeSaldo.reservering + reservering - inkomsten // reservering en inkomsten zijn negatief
                else if (reserveringRekeningGroepSoort.contains(basisPeriodeSaldo.rekening.rekeningGroep.rekeningGroepSoort))
                    basisPeriodeSaldo.openingsReserveSaldo + basisPeriodeSaldo.reservering + reservering - betaling
                else BigDecimal.ZERO
            logger.info(
                "rekening: ${basisPeriodeSaldo.rekening.naam} " +
                        "basisPeriodeSaldo.openingsReserveSaldo: ${basisPeriodeSaldo.openingsReserveSaldo}, " +
                        "basisPeriodeSaldo.reservering: ${basisPeriodeSaldo.reservering}, " +
                        "openingsReserveSaldo: ${openingsReserveSaldo}, " +
                        "reservering: $reservering, " +
                        "inkomsten: $inkomsten, " +
                        "betaling: $betaling"
            )

//            val aantalGeldigePeriodes = periodeRepository
//                .getPeriodesTussenDatums(
//                    basisPeriodeSaldo.rekening.rekeningGroep.gebruiker,
//                    basisPeriode.periodeStartDatum,
//                    peilPeriode.periodeStartDatum.minusDays(1)
//                )
//                .count {
//                    basisPeriodeSaldo.rekening.rekeningIsGeldigInPeriode(it)
//                            && basisPeriodeSaldo.rekening.rekeningVerwachtBetalingInPeriode(it)
//                }
//            val budgetMaandBedrag = basisPeriodeSaldo.rekening.toDTO(peilPeriode).budgetMaandBedrag ?: BigDecimal.ZERO
//            val achterstand = BigDecimal.ZERO
//                if (basisPeriodeSaldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
//                    (basisPeriodeSaldo.achterstand
//                            - (BigDecimal(aantalGeldigePeriodes) * budgetMaandBedrag)
//                            + basisPeriodeSaldo.betaling + betaling
//                            ).min(BigDecimal.ZERO)
//                else BigDecimal.ZERO
            basisPeriodeSaldo.fullCopy(
                openingsBalansSaldo = openingsBalansSaldo,
                openingsReserveSaldo = openingsReserveSaldo,
                achterstand = BigDecimal.ZERO,
            )
        }
        logger.info("openingsReserveSaldi: ${peilPeriode.periodeStartDatum} ${saldoLijst.joinToString { "${it.rekening.naam} -> ${it.openingsReserveSaldo}" }}")
        return saldoLijst
    }

    fun berekenMutatieLijstTussenDatums(gebruiker: Gebruiker, vanDatum: LocalDate, totDatum: LocalDate): List<Saldo> {
        val rekeningGroepLijst = rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(gebruiker, vanDatum, totDatum)
        val reserveringen = reserveringRepository.findAllByGebruikerTussenDatums(gebruiker, vanDatum, totDatum)
        val saldoLijst = rekeningGroepLijst.flatMap { rekeningGroep ->
            rekeningGroep.rekeningen.map { rekening ->
                val betaling =
                    betalingen.fold(BigDecimal.ZERO) { acc, betaling ->
                        acc + berekenBetalingMutaties(betaling, rekening)
                    }
                val reservering =
                    reserveringen.fold(BigDecimal.ZERO) { acc, reservering ->
                        acc + berekenReserveringMutaties(reservering, rekening)
                    }

                Saldo(0, rekening, betaling = betaling, reservering = reservering)
            }
        }
        logger.info("mutaties van $vanDatum tot $totDatum #betalingen: ${betalingen.size}: ${saldoLijst.joinToString { "${it.rekening.naam} -> ${it.betaling} + ${it.reservering}" }}")
        return saldoLijst
    }

    fun berekenBetalingMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.bron.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO +
                if (betaling.bestemming.id == rekening.id) betaling.bedrag else BigDecimal.ZERO
    }

    fun berekenReserveringMutaties(reservering: Reservering, rekening: Rekening): BigDecimal {
        return if (reservering.bron.id == rekening.id) -reservering.bedrag else BigDecimal.ZERO +
                if (reservering.bestemming.id == rekening.id) reservering.bedrag else BigDecimal.ZERO
    }


}
