package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.Saldo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SaldoRepository : JpaRepository<Saldo, Long> {
    fun findAllByPeriode(periode: Periode): List<Saldo>
    fun findOneByPeriodeAndRekening(periode: Periode, rekening: Rekening): Saldo?
    fun deleteByRekening(rekening: Rekening)
    fun findLastSaldoByRekening(rekening: Rekening): Optional<Saldo>
}