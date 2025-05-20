package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal

/*
    De Saldo tabel bevat het saldo van een rekening; door de relatie naar de Saldi tabel
    is het van 1 gebruiker, op 1 moment in de tijd
 */

@Entity
@Table(name = "saldo")
class Saldo(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    @ManyToOne
    @JoinColumn(name = "rekening_id", referencedColumnName = "id")
    val rekening: Rekening,
    val saldo: BigDecimal = BigDecimal(0),
    val achterstand: BigDecimal = BigDecimal(0),
    val budgetMaandBedrag: BigDecimal = BigDecimal(0),
    val budgetBetaling: BigDecimal = BigDecimal(0),
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "periode_id", referencedColumnName = "id")
    var periode: Periode? = null
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        saldo: BigDecimal = this.saldo,
        periode: Periode? = this.periode,
        achterstand: BigDecimal = this.achterstand,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        budgetBetaling: BigDecimal = this.budgetBetaling,
    ) = Saldo(this.id, rekening, saldo, achterstand, budgetMaandBedrag, budgetBetaling, periode)

    data class SaldoDTO(
        val id: Long = 0,
        val rekeningNaam: String,
        val saldo: BigDecimal = BigDecimal(0),
        val achterstand: BigDecimal = BigDecimal(0),
        val budgetMaandBedrag: BigDecimal = BigDecimal(0),
        val budgetBetaling: BigDecimal = BigDecimal(0),
    )

    fun toBalansDTO(): SaldoDTO {
        return SaldoDTO(
            this.id,
            this.rekening.naam,
            this.saldo,
            this.achterstand,
            this.budgetMaandBedrag,
            this.budgetBetaling,
        )
    }

    fun toResultaatDTO(): SaldoDTO {
        return SaldoDTO(
            this.id,
            this.rekening.naam,
            -this.saldo,
            this.achterstand,
            this.budgetMaandBedrag,
            this.budgetBetaling,
        )
    }
}
