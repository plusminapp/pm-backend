package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.RekeningGroep
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RekeningGroepRepository : JpaRepository<RekeningGroep, Long> {
    @Query(value = "SELECT rg FROM RekeningGroep rg WHERE rg.administratie = :administratie")
    fun findRekeningGroepenVoorAdministratie(administratie: Administratie ): List<RekeningGroep>

    @Query(value = "SELECT rg FROM RekeningGroep rg WHERE rg.administratie = :administratie AND rg.naam = :rekeningGroepNaam")
    fun findRekeningGroepOpNaam(administratie: Administratie, rekeningGroepNaam: String ): Optional<RekeningGroep>

    @Query(value = "SELECT rg FROM RekeningGroep rg WHERE rg.administratie = :administratie AND rg.rekeningGroepSoort = :rekeningGroepSoort")
    fun findRekeningGroepenOpSoort(administratie: Administratie, rekeningGroepSoort: RekeningGroep.RekeningGroepSoort ): List<RekeningGroep>
}

