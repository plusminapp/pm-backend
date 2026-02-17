package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Label
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LabelRepository : JpaRepository<Label, Long> {
    fun findByAdministratieAndNaam(administratie: Administratie, naam: String): Label?
}