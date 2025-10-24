package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesVoorNuRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.Integer.parseInt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ReserveringService {
    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var standStartVanPeriodeService: StandStartVanPeriodeService

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var cashflowService: CashflowService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun creeerReserveringen(gebruiker: Gebruiker) {
        val periodes = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .sortedBy { it.periodeStartDatum }
            .drop(1)
        periodes.forEach { periode ->
            creeerReserveringenVoorPeriode(gebruiker, periode.id)
        }
    }

    fun creeerReserveringenVoorPeriode(gebruiker: Gebruiker, periodeId: Long) {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        val reserveringBufferRekening = rekeningRepository.findBufferRekeningVoorGebruiker(gebruiker)
            ?: throw PM_BufferRekeningNotFoundException(listOf(gebruiker.bijnaam))

        val periode = periodeRepository.getPeriodeById(periodeId)
        if (periode == null) throw PM_PeriodeNotFoundException(listOf(gebruiker.bijnaam))
        val initieleStartSaldiVanPeriode: List<Saldo> =
            standStartVanPeriodeService.berekenStartSaldiVanPeriode(periode)
        val initieleBuffer =
            initieleStartSaldiVanPeriode.find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }?.openingsReserveSaldo
                ?: BigDecimal.ZERO
        val initieleReserveringTekorten =
            initieleStartSaldiVanPeriode.filter { potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) }
                .sumOf { if (it.openingsReserveSaldo < BigDecimal.ZERO) it.openingsReserveSaldo else BigDecimal.ZERO }
        logger.info("Initiele buffer bij start van periode ${periode.periodeStartDatum} voor ${gebruiker.bijnaam} is $initieleBuffer, reserveringstekorten $initieleReserveringTekorten, delta ${initieleBuffer + initieleReserveringTekorten}.")
        if (initieleBuffer + initieleReserveringTekorten < BigDecimal.ZERO) throw PM_OnvoldoendeBufferSaldoException(
            listOf(
                initieleBuffer.toString(),
                periode.periodeStartDatum.toString(),
                gebruiker.bijnaam,
                initieleReserveringTekorten.toString()
            )
        )

        val startSaldiVanPeriode = if (initieleReserveringTekorten == BigDecimal.ZERO) initieleStartSaldiVanPeriode
        else {
            initieleStartSaldiVanPeriode.map {
                    if (potjesRekeningGroepSoort.contains(it.rekening.rekeningGroep.rekeningGroepSoort) && it.openingsReserveSaldo < BigDecimal.ZERO) {
                        betalingRepository.save(
                            Betaling(
                                gebruiker = gebruiker,
                                boekingsdatum = periode.periodeStartDatum.minusDays(1),
                                reserveringsHorizon = periode.periodeStartDatum.minusDays(1),
                                bedrag = -it.openingsReserveSaldo,
                                omschrijving = "Correctie voor ${it.rekening.naam} in periode ${
                                    periode.periodeStartDatum.format(
                                        dateTimeFormatter
                                    )
                                }",
                                reserveringBron = reserveringBufferRekening,
                                reserveringBestemming = it.rekening,
                                sortOrder = berekenSortOrder(gebruiker, periode.periodeStartDatum.minusDays(1)),
                                betalingsSoort = Betaling.BetalingsSoort.P2P,
                            )
                        )
                        logger.warn("Buffer reserveringstekort van ${-it.openingsReserveSaldo} voor ${it.rekening.naam} bij start van periode ${periode.periodeStartDatum} voor ${gebruiker.bijnaam} aangevuld vanuit buffer.")
                        it.fullCopy(openingsReserveSaldo = BigDecimal.ZERO)
                    } else if (it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER) {
                        it.fullCopy(openingsReserveSaldo = it.openingsReserveSaldo + initieleReserveringTekorten)
                    } else it
                }
        }
        val rekeningGroepen = rekeningService.findRekeningGroepenMetGeldigeRekeningen(gebruiker, periode)
        val periodeLengte = periode.periodeEindDatum.toEpochDay() - periode.periodeStartDatum.toEpochDay() + 1
        val continueBudgetUitgaven = cashflowService.budgetContinueUitgaven(rekeningGroepen, periodeLengte)
        val betalingenInPeriode = betalingRepository.findAllByGebruikerTussenDatums(
                gebruiker,
                periode.periodeStartDatum,
                periode.periodeEindDatum
            ).filter {
                it.bron?.rekeningGroep?.budgetType !== RekeningGroep.BudgetType.SPAREN && it.bestemming?.rekeningGroep?.budgetType !== RekeningGroep.BudgetType.SPAREN
            }

        var reserveringsDatum = periode.periodeStartDatum
        var bufferSaldo =
            startSaldiVanPeriode.find { it.rekening.rekeningGroep.rekeningGroepSoort == RekeningGroep.RekeningGroepSoort.RESERVERING_BUFFER }?.openingsReserveSaldo
                ?: BigDecimal.ZERO
        logger.info("PRE: Buffer saldo op {} is {}, voor {}", reserveringsDatum, bufferSaldo, gebruiker.bijnaam)
        while (bufferSaldo >= BigDecimal.ZERO && reserveringsDatum.isBefore(periode.periodeEindDatum.plusDays(1))) {
            val nextBufferSaldo = bufferSaldo + continueBudgetUitgaven +
                    cashflowService.budgetVasteLastenUitgaven(rekeningGroepen, reserveringsDatum) +
                    cashflowService.betaaldeInkomsten(betalingenInPeriode, reserveringsDatum)
            if (nextBufferSaldo < BigDecimal.ZERO) break
            bufferSaldo = nextBufferSaldo
            logger.info(
                "Buffer saldo op {} is {} inclusief inkomsten {}, voor {}",
                reserveringsDatum,
                bufferSaldo,
                cashflowService.betaaldeInkomsten(betalingenInPeriode, reserveringsDatum),
                gebruiker.bijnaam
            )
            reserveringsDatum = reserveringsDatum.plusDays(1)
        }
        val budgetHorizon = reserveringsDatum.minusDays(1)
        logger.info("POST: Buffer saldo op ${budgetHorizon} is $bufferSaldo, voor ${gebruiker.bijnaam}")

        val rekeningen = rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
        val mutatiesInPeilPeriode = standStartVanPeriodeService.berekenMutatieLijstTussenDatums(
            gebruiker, periode.periodeStartDatum, budgetHorizon
        )
        logger.info(
            "mutatiesInPeilPeriode: van ${periode.periodeStartDatum} t/m $budgetHorizon ${
            mutatiesInPeilPeriode.joinToString {
                it.rekening.naam + " OBS " + it.openingsBalansSaldo + " ORS " + it.openingsReserveSaldo + " OOS " + it.openingsOpgenomenSaldo + " A " + it.achterstand + " BMB " + it.budgetMaandBedrag + " B " + it.betaling + " R " + it.reservering + " OS " + it.opgenomenSaldo
            }
        }")

        rekeningen.filter { potjesVoorNuRekeningGroepSoort.contains(it.rekeningGroep.rekeningGroepSoort) }
            .map { rekening ->
                creerReserveringVoorRekening(
                    mutatiesInPeilPeriode,
                    rekening,
                    startSaldiVanPeriode,
                    periode,
                    budgetHorizon,
                    gebruiker,
                    reserveringBufferRekening,
                    dateTimeFormatter
                )
            }
    }

    private fun creerReserveringVoorRekening(
        mutatiesInPeilPeriode: List<Saldo>,
        rekening: Rekening,
        startSaldiVanPeriode: List<Saldo>,
        periode: Periode,
        budgetHorizon: LocalDate,
        gebruiker: Gebruiker,
        reserveringBufferRekening: Rekening,
        dateTimeFormatter: DateTimeFormatter?
    ) {
        val reserveringBlaat =
            mutatiesInPeilPeriode.filter { it.rekening.naam == rekening.naam }.sumOf { it.reservering }

        val verrekenbareStartReservering =
            if (rekening.budgetAanvulling == Rekening.BudgetAanvulling.MET) BigDecimal.ZERO
            else startSaldiVanPeriode.find { it.rekening.id == rekening.id }?.openingsReserveSaldo ?: BigDecimal.ZERO

        val maandBedrag =
            (rekening.toDTO(periode).budgetMaandBedrag ?: BigDecimal.ZERO).minus(verrekenbareStartReservering)
        val budgetHorizonBedrag = (standInPeriodeService.berekenBudgetOpPeilDatum(
            rekening, budgetHorizon, maandBedrag, reserveringBlaat, periode
        )?.minus(verrekenbareStartReservering) ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
        val bedrag = maxOf(budgetHorizonBedrag.min(maandBedrag), BigDecimal.ZERO)
        logger.info(
            "creeerReservingenVoorPeriode: bedrag: $bedrag, rekening: ${rekening.naam}, " + "maandBedrag: $maandBedrag, betaling: $reserveringBlaat, BudgetAanvulling: ${rekening.budgetAanvulling}, " + "budgetHorizon: $budgetHorizon, budgetHorizonBedrag: $budgetHorizonBedrag, " + "periode: ${periode.periodeStartDatum} t/m ${periode.periodeEindDatum} " + "voor ${gebruiker.bijnaam}"
        )

        val opgeslagenReservering = betalingRepository.findByGebruikerOpDatumBronBestemming(
            gebruiker = gebruiker,
            datum = periode.periodeStartDatum,
            reserveringBron = reserveringBufferRekening,
            reserveringBestemming = rekening
        )
        if (bedrag > BigDecimal.ZERO) {
            if (opgeslagenReservering.isEmpty) {
                logger.info("Nieuwe reservering voor ${rekening.naam} op ${periode.periodeStartDatum} van $bedrag voor ${gebruiker.bijnaam}")
                betalingRepository.save(
                    Betaling(
                        gebruiker = gebruiker,
                        boekingsdatum = periode.periodeStartDatum,
                        reserveringsHorizon = budgetHorizon,
                        bedrag = maxOf(bedrag, BigDecimal.ZERO),
                        omschrijving = "Buffer voor ${rekening.naam} in periode " + "${
                            periode.periodeStartDatum.format(dateTimeFormatter)
                        }/" + "${periode.periodeEindDatum.format(dateTimeFormatter)}",
                        reserveringBron = reserveringBufferRekening,
                        reserveringBestemming = rekening,
                        sortOrder = berekenSortOrder(gebruiker, periode.periodeStartDatum),
                        betalingsSoort = Betaling.BetalingsSoort.P2P,
                    )
                )
            } else {
                logger.info("Update reservering voor ${rekening.naam} op ${periode.periodeStartDatum} van $bedrag voor ${gebruiker.bijnaam}")
                betalingRepository.save(
                    opgeslagenReservering.get().fullCopy(
                        bedrag = maxOf(bedrag, BigDecimal.ZERO), reserveringsHorizon = budgetHorizon
                    )
                )
            }
        }
    }

    fun berekenSortOrder(gebruiker: Gebruiker, boekingsDatum: LocalDate): String {
        val laatsteSortOrder: String? = betalingRepository.findLaatsteSortOrder(gebruiker, boekingsDatum)
        val sortOrderDatum = boekingsDatum.toString().replace("-", "")
        return if (laatsteSortOrder == null) "$sortOrderDatum.100"
        else {
            val sortOrderTeller = (parseInt(laatsteSortOrder.split(".")[1]) + 10).toString()
            "$sortOrderDatum.$sortOrderTeller"
        }
    }

//    fun update(oldReservering: Reservering, newReserveringDTO: ReserveringDTO): Reservering {
//        val gebruiker = oldReservering.gebruiker
//        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newReserveringDTO.bron)
//            ?: oldReservering.bron
//        val bestemming =
//            rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newReserveringDTO.bestemming)
//                ?: oldReservering.bestemming
//        val boekingsDatum = LocalDate.parse(newReserveringDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
//        val sortOrder = berekenSortOrder(gebruiker, boekingsDatum)
//        logger.info("Update reservering ${oldReservering.id}/${newReserveringDTO.omschrijving} voor ${gebruiker.bijnaam} ")
//        val newReservering = oldReservering.fullCopy(
//            boekingsdatum = boekingsDatum,
//            bedrag = newReserveringDTO.bedrag.toBigDecimal(),
//            omschrijving = newReserveringDTO.omschrijving,
//            bron = bron,
//            bestemming = bestemming,
//            sortOrder = sortOrder,
//        )
//        return betalingsRepository.save(newReservering)
//    }

//    fun findMatchingReservering(gebruiker: Gebruiker, reserveringDTO: ReserveringDTO): List<Reservering> {
//        return betalingsRepository.findMatchingReservering(
//            gebruiker = gebruiker,
//            boekingsdatum = LocalDate.parse(reserveringDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
//            bedrag = reserveringDTO.bedrag.toBigDecimal(),
//            omschrijving = reserveringDTO.omschrijving,
//        )
//    }

//    fun creeerReserveringenVoorStortenSpaargeld(gebruiker: Gebruiker, spaarRekeningNaam: String) {
//        val spaarRekening = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, spaarRekeningNaam)
//            ?: throw PM_RekeningNotFoundException(listOf(spaarRekeningNaam, gebruiker.bijnaam))
//        val spaarBetalingen = betalingsRepository
//            .findAllByGebruiker(gebruiker)
//            .filter { it.betalingsSoort == Betaling.BetalingsSoort.SPAREN }
//        spaarBetalingen.forEach {
//            val reserveringen = betalingsRepository.findMatchingReservering(
//                gebruiker = gebruiker,
//                boekingsdatum = it.boekingsdatum,
//                bedrag = it.bedrag,
//                omschrijving = it.omschrijving
//            )
//            if (reserveringen.isEmpty()) {
//                val bron = rekeningRepository.findBufferRekeningVoorGebruiker(gebruiker).firstOrNull()
//                      ?: throw PM_BufferRekeningNotFoundException(listOf(gebruiker.bijnaam))
//                betalingsRepository.save(
//                    Reservering(
//                        boekingsdatum = it.boekingsdatum,
//                        reserveringsHorizon = null,
//                        bedrag = it.bedrag,
//                        omschrijving = it.omschrijving,
//                        bron = bron,
//                        bestemming = spaarRekening,
//                        gebruiker = gebruiker
//                    )
//                )
//            }
//        }
//    }
}