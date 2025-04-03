package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RekeningRepository : JpaRepository<Rekening, Long> {
    @Query(value = "SELECT r FROM Rekening r WHERE r.gebruiker = :gebruiker")
    fun findRekeningenVoorGebruiker(gebruiker: Gebruiker): List<Rekening>

    @Query(value = "SELECT r FROM Rekening r " +
            "JOIN gebruiker g ON g = r.gebruiker " +
            "WHERE r.gebruiker = :gebruiker AND r.naam = :naam")
    fun findRekeningGebruikerEnNaam(gebruiker: Gebruiker, naam: String): Rekening?

    @Query(value ="SELECT * FROM rekening r ORDER BY r.sort_order DESC LIMIT 1",
        nativeQuery = true)
    fun findMaxSortOrder(): Rekening?
}
