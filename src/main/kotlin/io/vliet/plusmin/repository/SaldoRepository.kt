package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.Saldo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SaldoRepository : JpaRepository<Saldo, Long> {
    fun findAllByPeriode(periode: Periode): List<Saldo>
    fun findOneByPeriodeAndRekening(periode: Periode, rekening: Rekening): Saldo?
    fun deleteByRekening(rekening: Rekening)
    @Modifying
    fun deleteByPeriode(periode: Periode)

    @Query(value =
            "SELECT s.* FROM saldo s " +
                    "JOIN rekening r ON s.rekening_id = r.id " +
                    "JOIN periode p ON s.periode_id = p.id " +
                    "WHERE r.id = :rekeningId " +
                    "AND p.periode_start_datum = (" +
                    "SELECT MAX(p2.periode_start_datum) " +
                    "FROM saldo s2 " +
                    "JOIN periode p2 ON s2.periode_id = p2.id " +
                    "WHERE s2.rekening_id = :rekeningId)",
        nativeQuery = true)
    fun findLaatsteSaldoBijRekening(rekeningId: Long): Optional<Saldo>
}