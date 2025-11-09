package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface GebruikerRepository : JpaRepository<Gebruiker, Long> {
    fun findByEmail(email: String): Gebruiker?
    fun findBySubject(subject: String): Gebruiker?

    @Query(value = "SELECT g FROM Gebruiker g WHERE g.id = :id")
    fun selectById(id: Long): Gebruiker?
}
