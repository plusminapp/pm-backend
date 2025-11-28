package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Aflossing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface AflossingRepository : JpaRepository<Aflossing, Long> {
    fun save(aflossing: Aflossing): Aflossing

    @Query(
        "SELECT rg.administratie FROM RekeningGroep rg " +
                "JOIN rg.rekeningen r " +
                "WHERE r.aflossing = :aflossing"
    )
    fun findAdministratieByAflossing(aflossing: Aflossing): Administratie?
}
