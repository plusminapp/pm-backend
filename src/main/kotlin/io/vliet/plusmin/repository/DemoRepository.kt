package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Demo
import io.vliet.plusmin.domain.Gebruiker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DemoRepository : JpaRepository<Demo, Long> {
    fun findByGebruiker(gebruiker: Gebruiker): Demo?
}