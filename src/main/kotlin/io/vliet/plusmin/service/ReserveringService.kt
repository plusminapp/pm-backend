package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.Companion.inkomstenBetalingsSoorten
import io.vliet.plusmin.domain.Reservering.ReserveringDTO
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.ReserveringRepository
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

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun creeerReserveringLijst(gebruiker: Gebruiker, reserveringenLijst: List<ReserveringDTO>): List<ReserveringDTO> {
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
        return if (laatsteSortOrder == null) sortOrderDatum + ".900"
        else {
            val sortOrderTeller = (parseInt(laatsteSortOrder.split(".")[1]) - 10).toString()
            sortOrderDatum + "." + sortOrderTeller
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

    fun getReserveringenVoorHulpvrager(hulpvrager: Gebruiker): Map<Rekening?, Pair<BigDecimal, BigDecimal>> {
        val inkomsten = betalingsRepository
            .findAllByGebruiker(hulpvrager)
            .groupBy { if (inkomstenBetalingsSoorten.contains(it.betalingsSoort)) it.bron else null }
            .filter { it.key != null }
            .mapValues { entry -> entry.value.sumOf { it.bedrag } }
        val reserveringsBufferBedrag = inkomsten.values.sumOf { it }
        val reserveringsBufferRekening =
            rekeningRepository.findRekeningGebruikerEnNaam(hulpvrager, "_reservering_buffer")
                ?: throw IllegalStateException("Buffer rekening niet gevonden voor ${hulpvrager.bijnaam}.")
        val reserveringsBuffer = mapOf(reserveringsBufferRekening to reserveringsBufferBedrag)

        val uitgaven: Map<Rekening?, BigDecimal> = betalingsRepository
            .findAllByGebruiker(hulpvrager)
            .groupBy { if (inkomstenBetalingsSoorten.contains(it.betalingsSoort)) null else it.bestemming }
            .filterKeys { it != null }
            .mapValues { entry -> entry.value.sumOf { -it.bedrag } }

        val betalingen = uitgaven + reserveringsBuffer

        val reserveringen = reserveringRepository
            .findAllByGebruiker(hulpvrager)
            .flatMap { reservering ->
                listOfNotNull(
                    reservering.bron.let { it to -reservering.bedrag },
                    reservering.bestemming.let { it to reservering.bedrag }
                )
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.sumOf { it.second } }
        logger.info("Reserveringen voor ${hulpvrager.bijnaam}: reserveringen: ${reserveringen.map { "${it.key.naam}=${it.value}" }.joinToString(", ")}")

        return reserveringen.keys.union(betalingen.keys)
            .toSet()
            .associateWith { rekening ->
            val reservering = reserveringen
                .filter { it.key.id == rekening?.id }
                .values.sumOf { it }
            val betaling = betalingen
                .filter { it.key?.id == rekening?.id }
                .values.sumOf { it }
            logger.info(">>> ${rekening?.naam}/${rekening?.id}: reservering=$reservering - betaling=${betaling}")

            Pair(reservering, betaling)
        }
    }
}