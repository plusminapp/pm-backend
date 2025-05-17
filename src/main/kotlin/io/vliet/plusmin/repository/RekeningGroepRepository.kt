package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RekeningGroepRepository : JpaRepository<RekeningGroep, Long> {
    @Query(value = "SELECT r FROM RekeningGroep r WHERE r.gebruiker = :gebruiker AND r.naam = :rekeningGroepNaam")
    fun findRekeningGroepVoorGebruiker(gebruiker: Gebruiker, rekeningGroepNaam: String ): Optional<RekeningGroep>
}

