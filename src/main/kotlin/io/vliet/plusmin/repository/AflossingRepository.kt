package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Aflossing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface AflossingRepository : JpaRepository<Aflossing, Long> {
    @Query(
        value = "SELECT l FROM Aflossing l " +
                "JOIN rekening r ON r = l.rekening " +
                "JOIN rekeningGroep rg ON rg = r.rekeningGroep " +
                "WHERE rg.gebruiker = :gebruiker"
    )
    fun findAflossingenVoorGebruiker(gebruiker: Gebruiker): List<Aflossing>

    @Query(
        value = "SELECT l FROM Aflossing l " +
                "JOIN rekening r ON r = l.rekening " +
                "JOIN rekeningGroep rg ON rg = r.rekeningGroep " +
                "WHERE rg.gebruiker = :gebruiker AND r.naam = :rekeningNaam"
    )
    fun findAflossingVoorRekeningNaam(gebruiker: Gebruiker, rekeningNaam: String): Aflossing?
}