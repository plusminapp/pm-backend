package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
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
    @ManyToOne
    @JoinColumn(name = "van_periode_id")
    val vanPeriode: Periode? = null,
    @ManyToOne
    @JoinColumn(name = "tot_periode_id")
    val totEnMetPeriode: Periode? = null,
    val budgetBedrag: BigDecimal,
    @Enumerated(EnumType.STRING)
    val budgetPeriodiciteit: BudgetPeriodiciteit = BudgetPeriodiciteit.MAAND,
    val budgetBetaalDag: Int?,
    ) {
    companion object {
        val sortableFields = setOf("id", "naam")
    }

    fun fullCopy(
        naam: String = this.naam,
        rekeningGroep: RekeningGroep = this.rekeningGroep,
        sortOrder: Int = this.sortOrder,
        vanPeriode: Periode? = this.vanPeriode,
        totEnMetPeriode: Periode? = this.totEnMetPeriode,
        budgetBedrag: BigDecimal = this.budgetBedrag,
        budgetPeriodiciteit: BudgetPeriodiciteit = this.budgetPeriodiciteit,
        budgetBetaalDag: Int? = this.budgetBetaalDag,

        ) = Rekening(
        this.id,
        naam,
        rekeningGroep,
        sortOrder,
        vanPeriode,
        totEnMetPeriode,
        budgetBedrag,
        budgetPeriodiciteit,
        budgetBetaalDag,
    )
    enum class BudgetPeriodiciteit {
        WEEK, MAAND
    }

    data class RekeningDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningGroep: RekeningGroep.RekeningGroepDTO,
        val sortOrder: Int,
        val vanPeriode: Periode? = null,
        val totPeriode: Periode? = null,
        val budgetPeriodiciteit: String,
        val budgetBedrag: BigDecimal,
        val budgetBetaalDag: Int?,
        val budgetMaandBedrag: BigDecimal? = null,
        val budgetPeilDatum: String? = null,
        val budgetOpPeilDatum: BigDecimal? = null,
        val budgetBetaling: BigDecimal? = null,
        val betaaldBinnenBudget: BigDecimal? = null,
        val minderDanBudget: BigDecimal? = null,
        val meerDanBudget: BigDecimal? = null,
        val meerDanMaandBudget: BigDecimal? = null,
        val restMaandBudget: BigDecimal? = null,
    ) {
        fun fullCopy(
            naam: String = this.naam,
            rekeningGroep: RekeningGroep.RekeningGroepDTO = this.rekeningGroep,
            sortOrder: Int = this.sortOrder,
            vanPeriode: Periode? = this.vanPeriode,
            totPeriode: Periode? = this.totPeriode,
            budgetPeriodiciteit: String = this.budgetPeriodiciteit,
            budgetBedrag: BigDecimal = this.budgetBedrag,
            budgetBetaalDag: Int? = this.budgetBetaalDag,
            budgetMaandBedrag: BigDecimal? = this.budgetMaandBedrag,
            budgetPeilDatum: String? = this.budgetPeilDatum,
            budgetOpPeilDatum: BigDecimal? = this.budgetOpPeilDatum,
            budgetBetaling: BigDecimal? = this.budgetBetaling,
            betaaldBinnenBudget: BigDecimal? = this.betaaldBinnenBudget,
            minderDanBudget: BigDecimal? = this.minderDanBudget,
            meerDanBudget: BigDecimal? = this.meerDanBudget,
            meerDanMaandBudget: BigDecimal? = this.meerDanMaandBudget,
            restMaandBudget: BigDecimal? = this.restMaandBudget,
        ) = RekeningDTO(
            this.id, naam, rekeningGroep,
            sortOrder,
            vanPeriode,
            totPeriode,
            budgetPeriodiciteit,
            budgetBedrag,
            budgetBetaalDag,
            budgetMaandBedrag,
            budgetPeilDatum,
            budgetOpPeilDatum,
            budgetBetaling,
            betaaldBinnenBudget,
            minderDanBudget,
            meerDanBudget,
            meerDanMaandBudget,
            restMaandBudget,
        )
    }

    fun toDTO(
        budgetMaandBedrag: BigDecimal? = null,
        budgetPeilDatum: String? = null,
        budgetOpPeilDatum: BigDecimal? = null,
        budgetBetaling: BigDecimal? = null,
        betaaldBinnenBudget: BigDecimal? = null,
        minderDanBudget: BigDecimal? = null,
        meerDanBudget: BigDecimal? = null,
        meerDanMaandBudget: BigDecimal? = null,
        restMaandBudget: BigDecimal? = null,
    ): RekeningDTO {
        return RekeningDTO(
            this.id,
            this.naam,
            this.rekeningGroep.toDTO(),
            this.sortOrder,
            this.vanPeriode,
            this.totEnMetPeriode,
            this.budgetPeriodiciteit.toString(),
            this.budgetBedrag,
            this.budgetBetaalDag,
            budgetMaandBedrag,
            budgetPeilDatum,
            budgetOpPeilDatum,
            budgetBetaling,
            betaaldBinnenBudget,
            minderDanBudget,
            meerDanBudget,
            meerDanMaandBudget,
            restMaandBudget,
        )
    }
}
