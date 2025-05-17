package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.vliet.plusmin.domain.Budget.BudgetPeriodiciteit
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "rekening",
    uniqueConstraints = [UniqueConstraint(columnNames = ["gebruiker", "naam"])]
)
class Rekening(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    val naam: String,
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "rekening_groep_id", nullable = false)
    val rekeningGroep: RekeningGroep,
    val sortOrder: Int,
    val budgetPeriodiciteit: BudgetPeriodiciteit = BudgetPeriodiciteit.MAAND,
    val bedrag: BigDecimal,
    val betaalDag: Int?,
    @ManyToOne
    @JoinColumn(name = "van_periode_id")
    val vanPeriode: Periode? = null,
    @ManyToOne
    @JoinColumn(name = "tot_periode_id")
    val totEnMetPeriode: Periode? = null,

    ) {
    companion object {
        val sortableFields = setOf("id", "naam")
    }
    fun fullCopy(
        naam: String = this.naam,
        rekeningGroep: RekeningGroep = this.rekeningGroep,
        sortOrder: Int = this.sortOrder,
        budgetPeriodiciteit: BudgetPeriodiciteit = this.budgetPeriodiciteit,
        bedrag: BigDecimal = this.bedrag,
        betaalDag: Int? = this.betaalDag,
        vanPeriode: Periode? = this.vanPeriode,
        totEnMetPeriode: Periode? = this.totEnMetPeriode,

        ) = Rekening(
        this.id, naam, rekeningGroep, sortOrder, budgetPeriodiciteit,  bedrag, betaalDag, vanPeriode, totEnMetPeriode
    )

    data class RekeningDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningGroep: RekeningGroep.RekeningGroepDTO,
        val saldo: BigDecimal = BigDecimal(0),
        val sortOrder: Int,
    ){
        fun fullCopy(
            naam: String = this.naam,
            rekeningGroep: RekeningGroep.RekeningGroepDTO = this.rekeningGroep,
            saldo: BigDecimal = this.saldo,
            sortOrder: Int = this.sortOrder,
        ) = RekeningDTO(this.id, naam, rekeningGroep, saldo, sortOrder)
    }

    fun toDTO(): RekeningDTO {
        return RekeningDTO(
            this.id,
            this.naam,
            this.rekeningGroep.toDTO(),
            sortOrder = this.sortOrder
        )
    }
}
