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
        value = "SELECT rg FROM RekeningGroep rg " +
                "WHERE rg.gebruiker = :gebruiker")
    fun findRekeningGroepenVoorGebruiker(gebruiker: Gebruiker): List<RekeningGroep>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.gebruiker = :gebruiker")
    fun findRekeningenVoorGebruiker(gebruiker: Gebruiker): List<Rekening>

    @Query(value = "SELECT r FROM Rekening r " +
            "WHERE r.rekeningGroep = :rekeningGroep AND r.naam = :naam")
    fun findRekeningOpGroepEnNaam(rekeningGroep: RekeningGroep, naam: String): Optional<Rekening>

    @Query(value = "SELECT r FROM Rekening r " +
            "WHERE r.rekeningGroep.gebruiker = :gebruiker AND r.naam = :rekeningNaam")
    fun findRekeningGebruikerEnNaam(gebruiker: Gebruiker, rekeningNaam: String): Optional<Rekening>

    @Query(value ="SELECT * FROM rekening r ORDER BY r.sort_order DESC LIMIT 1",
        nativeQuery = true)
    fun findMaxSortOrder(): Optional<Rekening>
}
