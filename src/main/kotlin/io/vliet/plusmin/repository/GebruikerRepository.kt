package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GebruikerRepository : JpaRepository<Gebruiker, Long> {
    fun findByEmail(email: String): Gebruiker?

    @Query(value = "SELECT g FROM Gebruiker g WHERE g.id = :id")
    fun selectById(id: Long): Gebruiker?

    @Query(value = "SELECT g FROM Gebruiker g WHERE g.vrijwilliger = :vrijwilliger")
    fun findHulpvragersVoorVrijwilliger(vrijwilliger: Gebruiker): List<Gebruiker>
}
