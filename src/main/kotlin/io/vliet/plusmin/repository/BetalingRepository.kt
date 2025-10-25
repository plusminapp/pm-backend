package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Betaling.BetalingsSoort
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Repository
@Transactional
interface BetalingRepository : JpaRepository<Betaling, Long> {
    override fun findAll(): List<Betaling>
    fun findAllByGebruiker(gebruiker: Gebruiker): List<Betaling>

    @Query(value = "SELECT b FROM Betaling b WHERE b.id = :id")
    fun findById2(id: Long): Betaling?

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum <= :datum"
    )
    fun findAllByGebruikerTotEnMetDatum(gebruiker: Gebruiker, datum: LocalDate): List<Betaling>

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum >= :openingsDatum AND " +
                "b.boekingsdatum <= :eindDatum"
    )
    fun findAllByGebruikerTussenDatums(
        gebruiker: Gebruiker,
        openingsDatum: LocalDate,
        eindDatum: LocalDate
    ): List<Betaling>

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum >= :startDatum AND " +
                "b.boekingsdatum <= :eindDatum AND " +
                "b.reserveringBron.rekeningGroep.rekeningGroepSoort = 'RESERVERING_BUFFER' AND " +
                "b.reserveringBestemming.rekeningGroep.budgetType = 'SPAREN'"
    )
    fun findSpaarReserveringenInPeriode(
        gebruiker: Gebruiker,
        startDatum: LocalDate,
        eindDatum: LocalDate
    ): List<Betaling>
    
    @Query(
        value = "SELECT MAX(b.reserveringsHorizon) FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker"
    )
    fun getReserveringsHorizon(
        gebruiker: Gebruiker,
    ): LocalDate?

    @Modifying
    @Query(
        value = "DELETE FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum >= :openingsDatum AND " +
                "b.boekingsdatum <= :eindDatum"
    )
    fun deleteAllByGebruikerTussenDatums(
        gebruiker: Gebruiker,
        openingsDatum: LocalDate,
        eindDatum: LocalDate
    )

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum = :boekingsdatum AND " +
                "ABS(b.bedrag) = ABS(:bedrag) AND " +
                "b.omschrijving = :omschrijving AND " +
                "b.betalingsSoort = :betalingsSoort"
    )
    fun findMatchingBetaling(
        gebruiker: Gebruiker,
        boekingsdatum: LocalDate,
        bedrag: BigDecimal,
        omschrijving: String,
        betalingsSoort: BetalingsSoort
    ): List<Betaling>

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum = :boekingsdatum AND " +
                "ABS(b.bedrag) = ABS(:bedrag)"
    )
    fun findVergelijkbareBetalingen(
        gebruiker: Gebruiker,
        boekingsdatum: LocalDate,
        bedrag: BigDecimal,
    ): List<Betaling>

    @Query(
        value = "SELECT MAX(b.boekingsdatum) FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "(b.bron = :rekening OR b.bestemming = :rekening)"
    )
    fun findLaatsteBetalingDatumBijRekening(gebruiker: Gebruiker, rekening: Rekening): LocalDate?

    @Query(
        value = "SELECT MAX(b.boekingsdatum) FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker"
    )
    fun findDatumLaatsteBetalingBijGebruiker(gebruiker: Gebruiker): LocalDate?

    @Query(
        value = "SELECT MAX(b.sortOrder) FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum = :datum"
    )
    fun findLaatsteSortOrder(gebruiker: Gebruiker, datum: LocalDate): String?

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.gebruiker = :gebruiker AND " +
                "b.boekingsdatum = :datum AND " +
                "b.reserveringBron = :reserveringBron AND " +
                "b.reserveringBestemming = :reserveringBestemming"
    )
    fun findByGebruikerOpDatumBronBestemming(
        gebruiker: Gebruiker,
        datum: LocalDate,
        reserveringBron: Rekening,
        reserveringBestemming: Rekening
    ): List<Betaling>

    @Modifying
    @Query(
        value = "DELETE FROM Betaling b WHERE b.gebruiker = :gebruiker AND b.boekingsdatum <= :datum"
    )
    fun deleteAllByGebruikerTotEnMetDatum(gebruiker: Gebruiker, datum: LocalDate)
}
