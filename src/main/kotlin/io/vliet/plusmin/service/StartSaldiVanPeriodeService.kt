package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.Companion.internSparenBetalingsSoorten
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMiddelenRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.isPotjeVoorNu
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
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun updateOpeningsReserveringsSaldo(gebruiker: Gebruiker) {
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val basisPeriodeSaldi = saldoRepository.findAllByPeriode(basisPeriode)

        val saldoBetaalMiddelen = basisPeriodeSaldi
            .filter { betaalMiddelenRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sumOf { it.openingsBalansSaldo }
        val saldoPotjesVoorNu = basisPeriodeSaldi
            .filter { it.rekening.rekeningGroep.isPotjeVoorNu() }
            .sumOf { it.openingsReserveSaldo }
        logger.info(
            "Openings saldo betaalmiddelen: $saldoBetaalMiddelen, " +
                    "openings saldo potjes voor nu: $saldoPotjesVoorNu, " +
                    "totaal: ${saldoBetaalMiddelen - saldoPotjesVoorNu}"
        )
        val bufferReserveSaldo = basisPeriodeSaldi
            .find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }
            ?: throw IllegalStateException("RESERVERING_BUFFER Saldo voor periode ${basisPeriode.id} bestaat niet voor ${gebruiker.bijnaam}.")

        saldoRepository.save(
            bufferReserveSaldo.fullCopy(
                openingsReserveSaldo = saldoBetaalMiddelen - saldoPotjesVoorNu
            )
        )
    }

    fun berekenStartSaldiVanPeilPeriode(peilPeriode: Periode): List<Saldo> {
        val gebruiker = peilPeriode.gebruiker
        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)

        val basisPeriodeSaldi = saldoRepository.findAllByPeriode(basisPeriode)
        val betalingenTussenBasisEnPeilPeriode = berekenMutatieLijstTussenDatums(
            gebruiker,
            basisPeriode.periodeEindDatum.plusDays(1),
            peilPeriode.periodeStartDatum.minusDays(1)
        )
        val saldoLijst = basisPeriodeSaldi.map { basisPeriodeSaldo: Saldo ->
            val betaling = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeSaldo.rekening.naam }
                .sumOf { it.betaling }
            val reservering = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeSaldo.rekening.naam }
                .sumOf { it.reservering }
            val opgenomenSaldo = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeSaldo.rekening.naam }
                .sumOf { it.opgenomenSaldo }

            val openingsBalansSaldo =
                basisPeriodeSaldo.openingsBalansSaldo + basisPeriodeSaldo.betaling + betaling
            val openingsReserveSaldo =
                basisPeriodeSaldo.openingsReserveSaldo + basisPeriodeSaldo.reservering + reservering - betaling
            val openingsOpgenomenSaldo =
                basisPeriodeSaldo.openingsOpgenomenSaldo + basisPeriodeSaldo.opgenomenSaldo + opgenomenSaldo - betaling

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
                openingsOpgenomenSaldo = openingsOpgenomenSaldo,
                achterstand = BigDecimal.ZERO,
            )
        }
        logger.info("openingsSaldi: ${peilPeriode.periodeStartDatum} ${saldoLijst.joinToString { "${it.rekening.naam} -> B ${it.openingsBalansSaldo}  R ${it.openingsReserveSaldo}  O ${it.openingsOpgenomenSaldo}" }}")
        return saldoLijst
    }

    fun berekenMutatieLijstTussenDatums(gebruiker: Gebruiker, vanDatum: LocalDate, totDatum: LocalDate): List<Saldo> {
        val rekeningGroepLijst = rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(gebruiker, vanDatum, totDatum)
        val saldoLijst = rekeningGroepLijst.flatMap { rekeningGroep ->
            rekeningGroep.rekeningen.map { rekening ->
                val betaling =
                    betalingen
                        .filter { !internSparenBetalingsSoorten.contains(it.betalingsSoort) }
                        .fold(BigDecimal.ZERO) { acc, betaling ->
                            acc + berekenBetalingMutaties(betaling, rekening)
                    }
                val reservering =
                    betalingen.fold(BigDecimal.ZERO) { acc, betaling ->
                        acc + berekenReserveringMutaties(betaling, rekening)
                    }

                val opname =
                    betalingen
                        .filter { internSparenBetalingsSoorten.contains(it.betalingsSoort) }
                        .fold(BigDecimal.ZERO) { acc, betaling ->
                            acc + berekenBetalingMutaties(betaling, rekening)
                        }

                Saldo(0, rekening, betaling = betaling, reservering = reservering, opgenomenSaldo = opname)
            }
        }
        logger.info("berekenMutatieLijstTussenDatums van $vanDatum tot $totDatum #betalingen: ${betalingen.size}: ${saldoLijst.joinToString { "${it.rekening.naam} -> B ${it.betaling} + R ${it.reservering}" }}")
        return saldoLijst
    }

    fun berekenBetalingMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.bron?.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO +
                if (betaling.bestemming?.id == rekening.id) betaling.bedrag else BigDecimal.ZERO
    }

    fun berekenReserveringMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.reserveringBron?.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO +
                if (betaling.reserveringBestemming?.id == rekening.id) betaling.bedrag else BigDecimal.ZERO
    }
}
