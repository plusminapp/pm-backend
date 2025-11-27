package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Betaling.BetalingsSoort
import io.vliet.plusmin.domain.Administratie
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
    fun findAllByAdministratie(administratie: Administratie): List<Betaling>

    @Query(value = "SELECT b FROM Betaling b WHERE b.id = :id")
    fun findById2(id: Long): Betaling?

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum >= :openingsDatum AND " +
                "b.boekingsdatum <= :eindDatum"
    )
    fun findAllByAdministratieTussenDatums(
        administratie: Administratie,
        openingsDatum: LocalDate,
        eindDatum: LocalDate
    ): List<Betaling>

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum >= :startDatum AND " +
                "b.boekingsdatum <= :eindDatum AND " +
                "b.reserveringBron.rekeningGroep.rekeningGroepSoort = 'RESERVERING_BUFFER' AND " +
                "b.reserveringBestemming.rekeningGroep.budgetType = 'SPAREN'"
    )
    fun findSpaarReserveringenInPeriode(
        administratie: Administratie,
        startDatum: LocalDate,
        eindDatum: LocalDate
    ): List<Betaling>

    @Query(
        value = "SELECT MAX(b.reserveringsHorizon) FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie"
    )
    fun getReserveringsHorizon(
        administratie: Administratie,
    ): LocalDate?

    @Modifying
    @Query(
        value = "DELETE FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum >= :openingsDatum AND " +
                "b.boekingsdatum <= :eindDatum"
    )
    fun deleteAllByAdministratieTussenDatums(
        administratie: Administratie,
        openingsDatum: LocalDate,
        eindDatum: LocalDate
    )

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum = :boekingsdatum AND " +
                "ABS(b.bedrag) = ABS(:bedrag) AND " +
                "b.omschrijving = :omschrijving AND " +
                "b.betalingsSoort = :betalingsSoort"
    )
    fun findMatchingBetaling(
        administratie: Administratie,
        boekingsdatum: LocalDate,
        bedrag: BigDecimal,
        omschrijving: String,
        betalingsSoort: BetalingsSoort
    ): List<Betaling>

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum = :boekingsdatum AND " +
                "ABS(b.bedrag) = ABS(:bedrag)"
    )
    fun findVergelijkbareBetalingen(
        administratie: Administratie,
        boekingsdatum: LocalDate,
        bedrag: BigDecimal,
    ): List<Betaling>

    @Query(
        value = "SELECT MAX(b.boekingsdatum) FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "(b.bron = :rekening OR b.bestemming = :rekening)"
    )
    fun findLaatsteBetalingDatumBijRekening(administratie: Administratie, rekening: Rekening): LocalDate?

    @Query(
        value = "SELECT MAX(b.boekingsdatum) FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie"
    )
    fun findDatumLaatsteBetalingBijAdministratie(administratie: Administratie): LocalDate?

    @Query(
        value = "SELECT MAX(b.sortOrder) FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum = :datum"
    )
    fun findLaatsteSortOrder(administratie: Administratie, datum: LocalDate): String?

    @Query(
        value = "SELECT b FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "b.boekingsdatum = :datum AND " +
                "b.reserveringBron = :reserveringBron AND " +
                "b.reserveringBestemming = :reserveringBestemming"
    )
    fun findByAdministratieOpDatumBronBestemming(
        administratie: Administratie,
        datum: LocalDate,
        reserveringBron: Rekening,
        reserveringBestemming: Rekening
    ): List<Betaling>

    @Modifying
    @Query(
        value = "DELETE FROM Betaling b " +
                "WHERE b.isVerborgen IS FALSE AND " +
                "b.administratie = :administratie AND " +
                "boekingsdatum <= :datum"
    )

    fun deleteAllByAdministratieTotEnMetDatum(administratie: Administratie, datum: LocalDate)

    @Modifying
    @Query(
        value = "UPDATE Betaling b " +
                "SET b.isVerborgen = FALSE " +
                "WHERE b.administratie = :administratie AND " +
                "boekingsdatum <= :datum"
    )
    fun unhideAllByAdministratieTotEnMetDatum(administratie: Administratie, datum: LocalDate)
    @Modifying
    @Query(
        value = "UPDATE Betaling b " +
                "SET b.isVerborgen = TRUE " +
                "WHERE b.administratie = :administratie"
    )
    fun hideAllByAdministratie(administratie: Administratie)
}
