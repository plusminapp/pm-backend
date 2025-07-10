package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Demo
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Saldo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.query.Procedure
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface DemoRepository : JpaRepository<Demo, Long> {
    fun findByGebruiker(gebruiker: Gebruiker): Demo?

    @Modifying
    @Procedure("reset_gebruiker_data")
    fun resetGebruikerData(gebruikerId: Long)

}