package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.Reservering
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@Repository
interface ReserveringRepository : JpaRepository<Reservering, Long> {
    fun findAllByGebruiker(gebruiker: Gebruiker): List<Reservering>

    @Query(
        value = "SELECT r FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum >= :openingsDatum AND " +
                "r.boekingsdatum <= :eindDatum"
    )
    fun findAllByGebruikerTussenDatums(
        gebruiker: Gebruiker,
        openingsDatum: LocalDate,
        eindDatum: LocalDate
    ): List<Reservering>

    @Query(
        value = "SELECT r FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum <= :datum"
    )
    fun findAllByGebruikerVoorDatum(gebruiker: Gebruiker, datum: LocalDate): List<Reservering>

    @Query(
        value = "SELECT r FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum = :datum AND " +
                "r.bron = :bron AND " +
                "r.bestemming = :bestemming"
    )
    fun findByGebruikerOpDatumBronBestemming(
        gebruiker: Gebruiker,
        datum: LocalDate,
        bron: Rekening,
        bestemming: Rekening
    ): Optional<Reservering>

    @Query(
        value = "SELECT MAX(r.sortOrder) FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum = :datum"
    )
    fun findLaatsteSortOrder(gebruiker: Gebruiker, datum: LocalDate): String?

    @Query(
        value = "SELECT r FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum = :boekingsdatum AND " +
                "ABS(r.bedrag) = ABS(:bedrag) AND " +
                "r.omschrijving = :omschrijving "
    )
    fun findMatchingReservering(
        gebruiker: Gebruiker,
        boekingsdatum: LocalDate,
        bedrag: BigDecimal,
        omschrijving: String
    ): List<Reservering>

    @Query(
        value = "SELECT r FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum >= :startDatum AND " +
                "r.boekingsdatum <= :eindDatum AND " +
                "r.bron.rekeningGroep.rekeningGroepSoort = 'RESERVERING_BUFFER' AND " +
                "r.bestemming.rekeningGroep.budgetType = 'SPAREN'"
    )
    fun findSpaarReserveringenInPeriode(
        gebruiker: Gebruiker,
        startDatum: LocalDate,
        eindDatum: LocalDate
    ): List<Reservering>
}