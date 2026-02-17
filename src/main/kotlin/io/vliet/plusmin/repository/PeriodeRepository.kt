package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Periode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface PeriodeRepository : JpaRepository<Periode, Long> {

    /*  Haalt de Periode voor een administratie op waar datum in valt */
    @Query(
        value = "SELECT * FROM periode p WHERE p.administratie_id = :administratieId AND p.periode_start_datum = " +
                "(SELECT MAX(periode_start_datum) FROM periode p " +
                "WHERE p.administratie_id = :administratieId " +
                "AND p.periode_start_datum <= :datum " +
                "AND p.periode_eind_datum >= :datum)",
        nativeQuery = true
    )
    fun getPeriodeAdministratieEnDatum(administratieId: Long, datum: LocalDate): Periode?

    /*  Haalt de laatste Periode voor een administratie op */
    @Query(
        value = "SELECT * FROM periode p WHERE p.administratie_id = :administratieId AND p.periode_start_datum = " +
                "(SELECT MAX(periode_start_datum) FROM periode p WHERE p.administratie_id = :administratieId)",
        nativeQuery = true
    )
    fun getLaatstePeriodeVoorAdministratie(administratieId: Long): Periode?

    /*  Haalt de eerste Periode voor een administratie op */
    @Query(
        value = "SELECT * FROM periode p WHERE p.administratie_id = :administratieId AND p.periode_start_datum = " +
                "(SELECT MIN(periode_start_datum) FROM periode p WHERE p.administratie_id = :administratieId)",
        nativeQuery = true
    )
    fun getEerstePeriodeVoorAdministratie(administratieId: Long): Periode?

    @Query(value = "SELECT p FROM Periode p WHERE p.administratie = :administratie ORDER BY p.periodeStartDatum")
    fun getPeriodesVoorAdministrtatie(administratie: Administratie): List<Periode>

    @Query(
        value = "SELECT p FROM Periode p  " +
                "WHERE p.periodeStatus IN ('GESLOTEN', 'OPGERUIMD') " +
                "AND p.administratie = :administratie " +
                "ORDER BY p.periodeStartDatum DESC LIMIT 1"
    )
    fun getLaatstGeslotenOfOpgeruimdePeriode(administratie: Administratie): Periode?

    @Query(
        value = "SELECT p FROM Periode p  " +
                "WHERE p.periodeStartDatum >= :start " +
                "AND p.periodeEindDatum <= :eind " +
                "AND p.administratie = :administratie " +
                "ORDER BY p.periodeStartDatum ASC"
    )
    fun getPeriodesTussenDatums(administratie: Administratie, start: LocalDate, eind: LocalDate): List<Periode>

    fun getPeriodeById(periodeId: Long): Periode?
}

