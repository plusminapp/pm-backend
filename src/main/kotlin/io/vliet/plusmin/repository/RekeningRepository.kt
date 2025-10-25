package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RekeningRepository : JpaRepository<Rekening, Long> {
    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker"
    )
    fun findRekeningenVoorGebruiker(gebruiker: Gebruiker): List<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker " +
                "AND r.rekeningGroep.budgetType = 'SPAREN' "
    )
    fun findSpaarpottenVoorGebruiker(gebruiker: Gebruiker): List<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep = :rekeningGroep AND r.naam = :naam"
    )
    fun findRekeningOpGroepEnNaam(rekeningGroep: RekeningGroep, naam: String): Optional<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker " +
                "AND r.naam = :rekeningNaam"
    )
    fun findRekeningGebruikerEnNaam(gebruiker: Gebruiker, rekeningNaam: String): Rekening?

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker " +
                "AND r.gekoppeldeRekening.naam = :rekeningNaam " +
                "ORDER BY r.sortOrder ASC "
    )
    fun findGekoppeldeRekeningenGebruikerEnNaam(gebruiker: Gebruiker, rekeningNaam: String): List<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker " +
                "AND r.rekeningGroep.rekeningGroepSoort = 'SPAARPOT' " +
                "ORDER BY r.sortOrder ASC "
    )
    fun findSpaarPotRekeningenGebruiker(gebruiker: Gebruiker): List<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker " +
                "AND r.rekeningGroep.rekeningGroepSoort = 'RESERVERING_BUFFER' "
    )
    fun findBufferRekeningVoorGebruiker(gebruiker: Gebruiker): Rekening?


    @Query(
        value = "SELECT * FROM rekening r ORDER BY r.sort_order DESC LIMIT 1",
        nativeQuery = true
    )
    fun findMaxSortOrder(): Optional<Rekening>
}
