package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Label
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface LabelRepository : JpaRepository<Label, Long> {
    fun findByAdministratieAndNaam(administratie: Administratie, naam: String): Label?

    @Query(value = "SELECT * FROM label l WHERE l.administratie_id = :administratieId",
        nativeQuery = true)
    fun findByAdministratie(administratieId: Long): List<Label>
}