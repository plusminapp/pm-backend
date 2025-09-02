package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.Companion.inkomstenBetalingsSoorten
import io.vliet.plusmin.domain.Periode.Companion.geslotenPeriodes
import io.vliet.plusmin.domain.RekeningGroep.Companion.reserveringRekeningGroepSoort
import io.vliet.plusmin.domain.Reservering.ReserveringDTO
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.ReserveringRepository
import io.vliet.plusmin.repository.SaldoRepository
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
    lateinit var reserveringRepository: ReserveringRepository

    @Autowired
    lateinit var betalingsRepository: BetalingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var startSaldiVanPeriodeService: StartSaldiVanPeriodeService

    @Autowired
    lateinit var standInPeriodeService: StandInPeriodeService

    @Autowired
    lateinit var cashflowService: CashflowService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun creeerReserveringen(gebruiker: Gebruiker, reserveringenLijst: List<ReserveringDTO>): List<ReserveringDTO> {
        return reserveringenLijst.map { reserveringDTO ->
            creeerReservering(gebruiker, reserveringDTO)
        }
    }

    fun creeerReservering(gebruiker: Gebruiker, reserveringDTO: ReserveringDTO): ReserveringDTO {
        val boekingsDatum = LocalDate.parse(reserveringDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val periode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, boekingsDatum)
        if (periode == null || (periode.periodeStatus != Periode.PeriodeStatus.OPEN && periode.periodeStatus != Periode.PeriodeStatus.HUIDIG)) {
            throw IllegalStateException("Op $boekingsDatum is er geen OPEN periode voor ${gebruiker.bijnaam}.")
        }
        val reserveringList = this.findMatchingReservering(gebruiker, reserveringDTO)
        val reservering = if (reserveringList.isNotEmpty()) {
            logger.info("Reservering bestaat al: ${reserveringList[0].omschrijving} met id ${reserveringList[0].id} voor ${gebruiker.bijnaam}")
            update(reserveringList[0], reserveringDTO)
        } else {
            val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, reserveringDTO.bron)
                ?: throw IllegalStateException("${reserveringDTO.bron} bestaat niet voor ${gebruiker.bijnaam}.")
            val bestemming =
                rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, reserveringDTO.bestemming)
                    ?: throw IllegalStateException("${reserveringDTO.bestemming} bestaat niet voor ${gebruiker.bijnaam}.")
            val sortOrder = berekenSortOrder(gebruiker, boekingsDatum)
            logger.info("Opslaan reservering ${reserveringDTO.omschrijving} voor ${gebruiker.bijnaam}")
            Reservering(
                gebruiker = gebruiker,
                boekingsdatum = LocalDate.parse(reserveringDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
                bedrag = reserveringDTO.bedrag.toBigDecimal(),
                omschrijving = reserveringDTO.omschrijving,
                bron = bron,
                bestemming = bestemming,
                sortOrder = sortOrder,
            )
        }
        return reserveringRepository.save(reservering).toDTO()
    }

    fun berekenSortOrder(gebruiker: Gebruiker, boekingsDatum: LocalDate): String {
        val laatsteSortOrder: String? = reserveringRepository.findLaatsteSortOrder(gebruiker, boekingsDatum)
        val sortOrderDatum = boekingsDatum.toString().replace("-", "")
        return if (laatsteSortOrder == null) "$sortOrderDatum.100"
        else {
            val sortOrderTeller = (parseInt(laatsteSortOrder.split(".")[1]) + 10).toString()
            "$sortOrderDatum.$sortOrderTeller"
        }
    }

    fun update(oldReservering: Reservering, newReserveringDTO: ReserveringDTO): Reservering {
        val gebruiker = oldReservering.gebruiker
        val bron = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newReserveringDTO.bron)
            ?: oldReservering.bron
        val bestemming =
            rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, newReserveringDTO.bestemming)
                ?: oldReservering.bestemming
        val boekingsDatum = LocalDate.parse(newReserveringDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE)
        val sortOrder = berekenSortOrder(gebruiker, boekingsDatum)
        logger.info("Update reservering ${oldReservering.id}/${newReserveringDTO.omschrijving} voor ${gebruiker.bijnaam} ")
        val newReservering = oldReservering.fullCopy(
            boekingsdatum = boekingsDatum,
            bedrag = newReserveringDTO.bedrag.toBigDecimal(),
            omschrijving = newReserveringDTO.omschrijving,
            bron = bron,
            bestemming = bestemming,
            sortOrder = sortOrder,
        )
        return reserveringRepository.save(newReservering)
    }

    fun findMatchingReservering(gebruiker: Gebruiker, reserveringDTO: ReserveringDTO): List<Reservering> {
        return reserveringRepository.findMatchingReservering(
            gebruiker = gebruiker,
            boekingsdatum = LocalDate.parse(reserveringDTO.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
            bedrag = reserveringDTO.bedrag.toBigDecimal(),
            omschrijving = reserveringDTO.omschrijving,
        )
    }

    fun getReserveringenEnBetalingenVoorHulpvrager(
        hulpvrager: Gebruiker,
        datum: LocalDate
    ): Map<Rekening?, Pair<BigDecimal, BigDecimal>> {
        val inkomsten = betalingsRepository
            .findAllByGebruikerTotEnMetDatum(hulpvrager, datum)
            .groupBy { if (inkomstenBetalingsSoorten.contains(it.betalingsSoort)) it.bron else null }
            .filter { it.key != null }
            .mapValues { entry -> entry.value.sumOf { it.bedrag } }
        val reserveringsBufferBedrag = inkomsten.values.sumOf { it }
        val bufferRekeningen = rekeningRepository.findBufferRekeningVoorGebruiker(hulpvrager)
        val reserveringsBufferRekening = bufferRekeningen.firstOrNull()
            ?: throw IllegalStateException("Buffer rekening niet gevonden voor ${hulpvrager.bijnaam}.")
        val reserveringsBuffer = mapOf(reserveringsBufferRekening to reserveringsBufferBedrag)

        val uitgaven: Map<Rekening?, BigDecimal> = betalingsRepository
            .findAllByGebruikerTotEnMetDatum(hulpvrager, datum)
            .groupBy { if (!inkomstenBetalingsSoorten.contains(it.betalingsSoort)) it.bestemming else null }
            .filterKeys { it != null }
            .mapValues { entry -> entry.value.sumOf { -it.bedrag } }

        val betalingen = uitgaven + reserveringsBuffer

        val reserveringen = reserveringRepository
            .findAllByGebruikerVoorDatum(hulpvrager, datum)
            .flatMap { reservering ->
                listOfNotNull(
                    reservering.bron.let { it to -reservering.bedrag },
                    reservering.bestemming.let { it to reservering.bedrag }
                )
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.sumOf { it.second } }
        logger.info(
            "Reserveringen voor ${hulpvrager.bijnaam}: reserveringen: ${
                reserveringen.map { "${it.key.naam}=${it.value}" }.joinToString(", ")
            }"
        )

        val reserveringenEnBetalingenPerRekening = reserveringen.keys.union(betalingen.keys)
            .toSet()
            .associateWith { rekening ->
                val reservering = reserveringen
                    .filter { it.key.id == rekening?.id }
                    .values.sumOf { it }
                val betaling = betalingen
                    .filter { it.key?.id == rekening?.id }
                    .values.sumOf { it }
                Pair(reservering, betaling)
            }

        return reserveringenEnBetalingenPerRekening
    }

    fun creeerReservingenVoorPeriode(periode: Periode) {
        val startSaldiVanPeriode: List<Saldo> =
            if (geslotenPeriodes.contains(periode.periodeStatus)) {
                saldoRepository
                    .findAllByPeriode(periode)
                    .filter { it.rekening.rekeningIsGeldigInPeriode(periode) }
            } else {
                startSaldiVanPeriodeService.berekenStartSaldiVanPeilPeriode(periode)
            }
        val gebruiker = periode.gebruiker
        val budgetHorizon = cashflowService.getBudgetHorizon(gebruiker, periode) ?: periode.periodeStartDatum
        val rekeningen = rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
        val mutatiesInPeilPeriode =
            startSaldiVanPeriodeService.berekenMutatieLijstTussenDatums(
                gebruiker,
                periode.periodeStartDatum,
                budgetHorizon
            )

        val reserveringBufferRekening = rekeningRepository.findBufferRekeningVoorGebruiker(gebruiker).firstOrNull()
            ?: throw IllegalStateException("Buffer rekening niet gevonden voor ${gebruiker.bijnaam}.")
        val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        rekeningen
            .filter { reserveringRekeningGroepSoort.contains(it.rekeningGroep.rekeningGroepSoort) }
            .map { rekening ->
                val betaling = mutatiesInPeilPeriode
                    .filter { it.rekening.naam == rekening.naam }
//                    .filter { it.rekening.rekeningGroep.budgetType != RekeningGroep.BudgetType.SPAREN }
                    .sumOf { it.betaling }

                val startSaldoVanPeriode = startSaldiVanPeriode
                    .find { it.rekening.id == rekening.id }
                    ?.openingsReserveSaldo ?: BigDecimal.ZERO

                val maandBedrag =
                    (rekening.toDTO(periode).budgetMaandBedrag ?: BigDecimal.ZERO) -
                            (if (rekening.budgetAanvulling != Rekening.BudgetAanvulling.MET) startSaldoVanPeriode
                            else BigDecimal.ZERO)
                val budgetHorizonBedrag = (standInPeriodeService.berekenBudgetOpPeilDatum(
                    rekening,
                    budgetHorizon,
                    maandBedrag,
                    betaling,
                    periode
                )?.minus(startSaldoVanPeriode) ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
                val bedrag = maxOf(budgetHorizonBedrag.min(maandBedrag), BigDecimal.ZERO)
                logger.info(
                    "creeerReservingenVoorPeriode: bedrag: $bedrag, rekening: ${rekening.naam}, " +
                            "maandBedrag: $maandBedrag, betaling: $betaling, " +
                            "budgetHorizon: $budgetHorizon, budgetHorizonBedrag: $budgetHorizonBedrag, " +
                            "periode: ${periode.periodeStartDatum} t/m ${periode.periodeEindDatum} " +
                            "voor ${gebruiker.bijnaam}"
                )

                val reservering = reserveringRepository
                    .findByGebruikerOpDatumBronBestemming(
                        gebruiker = gebruiker,
                        datum = periode.periodeStartDatum,
                        bron = reserveringBufferRekening,
                        bestemming = rekening
                    )
                if (bedrag > BigDecimal.ZERO) {
                    if (reservering.isEmpty) {
                        reserveringRepository.save(
                            Reservering(
                                gebruiker = gebruiker,
                                boekingsdatum = periode.periodeStartDatum,
                                bedrag = maxOf(bedrag, BigDecimal.ZERO),
                                omschrijving = "Buffer voor ${rekening.naam} in periode " +
                                        "${periode.periodeStartDatum.format(dateTimeFormatter)}/" +
                                        "${periode.periodeEindDatum.format(dateTimeFormatter)}",
                                bron = reserveringBufferRekening,
                                bestemming = rekening,
                                sortOrder = berekenSortOrder(gebruiker, periode.periodeStartDatum)
                            )
                        )
                    } else
                        reserveringRepository.save(
                            reservering.get().fullCopy(
                                bedrag = maxOf(bedrag, BigDecimal.ZERO),
                            )
                        )
                }
            }
    }

    fun creeerReservingenVoorAllePeriodes(gebruiker: Gebruiker) {
        val periodes = periodeRepository
            .getPeriodesVoorGebruiker(gebruiker)
            .filter { !it.periodeStartDatum.equals(it.periodeEindDatum) }
            .sortedBy { it.periodeStartDatum }
        periodes.forEach {
            logger.info("creeerReservingenVoorPeriode ${it.periodeStartDatum} t/m ${it.periodeEindDatum} voor ${gebruiker.bijnaam}")
            creeerReservingenVoorPeriode(it)
        }
    }

    fun creeerReserveringenVoorStortenSpaargeld(gebruiker: Gebruiker, spaarRekeningNaam: String) {
        val spaarRekening = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, spaarRekeningNaam)
            ?: throw IllegalStateException("Spaarrekening $spaarRekeningNaam niet gevonden voor ${gebruiker.bijnaam}.")
        val spaarBetalingen = betalingsRepository
            .findAllByGebruiker(gebruiker)
            .filter { it.betalingsSoort == Betaling.BetalingsSoort.SPAREN }
        spaarBetalingen.forEach {
            val reserveringen = reserveringRepository.findMatchingReservering(
                gebruiker = gebruiker,
                boekingsdatum = it.boekingsdatum,
                bedrag = it.bedrag,
                omschrijving = it.omschrijving
            )
            if (reserveringen.isEmpty()) {
                val bron = rekeningRepository.findBufferRekeningVoorGebruiker(gebruiker).firstOrNull()
                    ?: throw IllegalStateException("Buffer rekening niet gevonden voor ${gebruiker.bijnaam}.")
                reserveringRepository.save(
                    Reservering(
                        boekingsdatum = it.boekingsdatum,
                        bedrag = it.bedrag,
                        omschrijving = it.omschrijving,
                        bron = bron,
                        bestemming = spaarRekening,
                        gebruiker = gebruiker
                    )
                )
            }
        }
    }
}