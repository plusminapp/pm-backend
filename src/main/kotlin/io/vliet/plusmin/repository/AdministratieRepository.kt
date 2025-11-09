package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Gebruiker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface AdministratieRepository : JpaRepository<Administratie, Long> {

    @Query("select distinct a from Gebruiker g join g.administraties a where g = :gebruiker")
    fun findAdministratiesVoorGebruiker(gebruiker: Gebruiker): List<Administratie>

}

