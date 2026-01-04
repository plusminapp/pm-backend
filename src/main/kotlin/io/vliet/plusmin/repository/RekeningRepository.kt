package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RekeningRepository : JpaRepository<Rekening, Long> {
    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie"
    )
    fun findRekeningenVoorAdministratie(administratie: Administratie): List<Rekening>

    @Query(
        value = "SELECT r FROM RekeningGroep r " +
                "WHERE r.administratie = :administratie"
    )
    fun findRekeningGroepenVoorAdministratie(administratie: Administratie): List<RekeningGroep>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie " +
                "AND r.rekeningGroep.budgetType = 'SPAREN' "
    )
    fun findSpaarpottenVoorAdministratie(administratie: Administratie): List<Rekening>
    @Query(
        value = "SELECT r.budgetBetaalDag FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie " +
                "AND r.rekeningGroep.rekeningGroepSoort = 'INKOMSTEN' " +
                "ORDER BY r.budgetBetaalDag ASC "
    )
    fun findBetaalDagenVoorAdministratie(administratie: Administratie): List<Int?>
    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep = :rekeningGroep AND r.naam = :naam"
    )
    fun findRekeningOpGroepEnNaam(rekeningGroep: RekeningGroep, naam: String): Optional<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie " +
                "AND r.naam = :rekeningNaam"
    )
    fun findRekeningAdministratieEnNaam(administratie: Administratie, rekeningNaam: String): Rekening?

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie " +
                "AND r.rekeningGroep.rekeningGroepSoort = 'BETAALREKENING' " +
                "ORDER BY r.sortOrder ASC "
    )
    fun findBetaalRekeningenAdministratie(administratie: Administratie): List<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie " +
                "AND r.rekeningGroep.rekeningGroepSoort = 'SPAARPOT' " +
                "ORDER BY r.sortOrder ASC "
    )
    fun findSpaarPotRekeningenAdministratie(administratie: Administratie): List<Rekening>

    @Query(
        value = "SELECT r FROM Rekening r " +
                "WHERE r.rekeningGroep.administratie = :administratie " +
                "AND r.rekeningGroep.rekeningGroepSoort = 'RESERVERING_BUFFER' "
    )
    fun findBufferRekeningVoorAdministratie(administratie: Administratie): Rekening?


    @Query(
        value = "SELECT * FROM rekening r ORDER BY r.sort_order DESC LIMIT 1",
        nativeQuery = true
    )
    fun findMaxSortOrder(): Optional<Rekening>
}
