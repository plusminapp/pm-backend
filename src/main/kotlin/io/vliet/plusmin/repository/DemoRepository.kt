package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Demo
import io.vliet.plusmin.domain.Administratie
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.query.Procedure
import org.springframework.stereotype.Repository

@Repository
interface DemoRepository : JpaRepository<Demo, Long> {
    fun findByAdministratie(administratie: Administratie): Demo?

//    @Modifying
//    @Procedure("reset_gebruiker_data")
//    fun resetGebruikerData(gebruikerId: Long)

}