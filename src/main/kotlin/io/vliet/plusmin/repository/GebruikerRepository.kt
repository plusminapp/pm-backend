package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface GebruikerRepository : JpaRepository<Gebruiker, Long> {
    fun findBySubject(subject: String): Gebruiker?

    @Query(value = "SELECT g FROM Gebruiker g WHERE g.id = :id")
    fun selectById(id: Long): Gebruiker?

    @Modifying
    @Transactional
    @Query("UPDATE Gebruiker g SET g.bijnaam = :bijnaam WHERE g.id = :id")
    fun updateBijnaamById(id: Long, bijnaam: String): Int
}
