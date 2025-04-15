package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "budget",
    uniqueConstraints = [UniqueConstraint(columnNames = ["rekening", "naam"])]
)
class Budget(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "rekening_id", nullable = false)
    val rekening: Rekening,
    val budgetNaam: String,
    val budgetPeriodiciteit: BudgetPeriodiciteit = BudgetPeriodiciteit.MAAND,
    val bedrag: BigDecimal,
    val betaalDag: Int?,
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        budgetNaam: String = this.budgetNaam,
        bedrag: BigDecimal = this.bedrag,
        budgetPeriodiciteit: BudgetPeriodiciteit = this.budgetPeriodiciteit,
        betaalDag: Int? = this.betaalDag,
    ) = Budget(this.id, rekening, budgetNaam, budgetPeriodiciteit, bedrag, betaalDag)

    data class BudgetDTO(
        val id: Long = 0,
        val rekeningNaam: String,
        val rekeningSoort: String,
        val budgetNaam: String,
        val budgetPeriodiciteit: String,
        val bedrag: BigDecimal,
        val betaalDag: Int?,
        val budgetPeilDatum: String? = null,
        val budgetOpPeilDatum: BigDecimal? = null,
        val budgetBetaling: BigDecimal? = null)

    fun toDTO(budgetPeilDatum: String? = null, budgetOpPeilDatum: BigDecimal? = null, budgetBetaling: BigDecimal? = null): BudgetDTO {
        return BudgetDTO(
            this.id,
            this.rekening.naam,
            this.rekening.rekeningSoort.toString(),
            this.budgetNaam,
            this.budgetPeriodiciteit.toString(),
            this.bedrag,
            this.betaalDag,
            budgetPeilDatum,
            budgetOpPeilDatum,
            budgetBetaling
        )
    }

    enum class BudgetType {
        VAST, CONTINU
    }

    enum class BudgetPeriodiciteit {
        WEEK, MAAND
    }
}
