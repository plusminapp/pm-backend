package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Reservering
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

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
        value = "SELECT MIN(r.sortOrder) FROM Reservering r " +
                "WHERE r.gebruiker = :gebruiker AND " +
                "r.boekingsdatum = :datum"
    )
    fun findLaatsteSortOrder(gebruiker: Gebruiker, datum: LocalDate): String?

    @Query(
        value = "SELECT b FROM Reservering b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum = :boekingsdatum AND " +
                "ABS(b.bedrag) = ABS(:bedrag) AND " +
                "b.omschrijving = :omschrijving "
    )
    fun findMatchingReservering(
        gebruiker: Gebruiker,
        boekingsdatum: LocalDate,
        bedrag: BigDecimal,
        omschrijving: String
    ): List<Reservering>

}