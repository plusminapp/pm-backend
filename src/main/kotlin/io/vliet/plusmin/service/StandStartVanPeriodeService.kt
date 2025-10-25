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
    lateinit var standEindeVanGeslotenPeriodeService: StandEindeVanGeslotenPeriodeService

    @Autowired
    lateinit var standMutatiesTussenDatumsService: StandMutatiesTussenDatumsService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun berekenStartSaldiVanPeriode(gebruiker: Gebruiker, periodeId: Long): List<Saldo.SaldoDTO> {
        val periode = periodeRepository.findById(periodeId)
            .orElseThrow { PM_PeriodeNotFoundException(listOf(periodeId.toString(), gebruiker.bijnaam)) }
        if (periode.gebruiker.id != gebruiker.id)
            throw PM_PeriodeNotFoundException(listOf(periodeId.toString(), gebruiker.bijnaam))
        return berekenStartSaldiVanPeriode(periode)
            .filter { balansRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
            .sortedBy { it.rekening.sortOrder }
            .map { it.toDTO() }
    }

    fun berekenStartSaldiVanPeriode(periode: Periode): List<Saldo> {

        val gebruiker = periode.gebruiker
        logger.info("berekenStartSaldiVanPeriode: periode: ${periode.periodeStartDatum} voor gebruiker ${gebruiker.bijnaam}")
        val vorigePeriode = periodeRepository
            .getPeriodeGebruikerEnDatum(gebruiker.id, periode.periodeStartDatum.minusDays(1))
            ?: throw PM_NoPeriodException(listOf(periode.periodeStartDatum.minusDays(1).toString(), gebruiker.bijnaam))
        if (geslotenPeriodes.contains(vorigePeriode.periodeStatus))
            return standEindeVanGeslotenPeriodeService.berekenEindSaldiVanGeslotenPeriode(vorigePeriode)

        val basisPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
//        val vorigPeriodeIsBasisPeriode = basisPeriode.periodeEindDatum.plusDays(1).equals(periode.periodeStartDatum)

        val basisPeriodeEindSaldi = standEindeVanGeslotenPeriodeService.berekenEindSaldiVanGeslotenPeriode(basisPeriode)
        val betalingenTussenBasisEnPeilPeriode = standMutatiesTussenDatumsService.berekenMutatieLijstTussenDatums(
            gebruiker,
            basisPeriode.periodeEindDatum.plusDays(1),
            periode.periodeStartDatum.minusDays(1)
        )
        val saldoLijst = basisPeriodeEindSaldi.map { basisPeriodeEindSaldo: Saldo ->
            val betaling = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.betaling }
            val reservering = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.reservering }
            val opgenomenSaldo = betalingenTussenBasisEnPeilPeriode
                .filter { it.rekening.naam == basisPeriodeEindSaldo.rekening.naam }
                .sumOf { it.opgenomenSaldo }

            val openingsBalansSaldo =
                basisPeriodeEindSaldo.openingsBalansSaldo + betaling
            val openingsReserveSaldo =
                basisPeriodeEindSaldo.openingsReserveSaldo + reservering - betaling
            val openingsOpgenomenSaldo =
                basisPeriodeEindSaldo.openingsOpgenomenSaldo + opgenomenSaldo - betaling

//            val aantalGeldigePeriodes = periodeRepository
//                .getPeriodesTussenDatums(
//                    basisPeriodeEindSaldo.rekening.rekeningGroep.gebruiker,
//                    basisPeriode.periodeStartDatum,
//                    peilPeriode.periodeStartDatum.minusDays(1)
//                )
//                .count {
//                    basisPeriodeEindSaldo.rekening.rekeningIsGeldigInPeriode(it)
//                            && basisPeriodeEindSaldo.rekening.rekeningVerwachtBetalingInPeriode(it)
//                }
//            val budgetMaandBedrag = basisPeriodeEindSaldo.rekening.toDTO(peilPeriode).budgetMaandBedrag ?: BigDecimal.ZERO
//            val achterstand = BigDecimal.ZERO
//                if (basisPeriodeEindSaldo.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST)
//                    (basisPeriodeEindSaldo.achterstand
//                            - (BigDecimal(aantalGeldigePeriodes) * budgetMaandBedrag)
//                            + basisPeriodeEindSaldo.betaling + betaling
//                            ).min(BigDecimal.ZERO)
//                else BigDecimal.ZERO
            basisPeriodeEindSaldo.fullCopy(
                openingsBalansSaldo = openingsBalansSaldo,
                openingsReserveSaldo = openingsReserveSaldo,
                openingsOpgenomenSaldo = openingsOpgenomenSaldo,
                correctieBoeking = BigDecimal.ZERO,
                achterstand = BigDecimal.ZERO,
            )
        }
        logger.info("openingsSaldi: ${periode.periodeStartDatum} ${saldoLijst.joinToString { "${it.rekening.naam} -> B ${it.openingsBalansSaldo}  R ${it.openingsReserveSaldo}  O ${it.openingsOpgenomenSaldo} C ${it.correctieBoeking}" }}")
        return saldoLijst
    }
}
