package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "rekening",
    uniqueConstraints = [UniqueConstraint(columnNames = ["gebruiker", "naam"])]
)
@JsonInclude( JsonInclude.Include.NON_EMPTY)
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
    val bankNaam: String? = null,
    @ManyToOne
    @JoinColumn(name = "van_periode_id")
    val vanPeriode: Periode? = null,
    @ManyToOne
    @JoinColumn(name = "tot_periode_id")
    val totEnMetPeriode: Periode? = null,
    val budgetBedrag: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    val budgetPeriodiciteit: BudgetPeriodiciteit? = null,
    val budgetBetaalDag: Int? = null,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "rekening_betaal_methoden",
        joinColumns = [JoinColumn(name = "rekening_id")],
        inverseJoinColumns = [JoinColumn(name = "betaalmethode_id")]
    )
    var betaalMethoden: List<Rekening> = emptyList()
) {
    companion object {
        val sortableFields = setOf("id", "naam")
    }

    fun fullCopy(
        naam: String = this.naam,
        rekeningGroep: RekeningGroep = this.rekeningGroep,
        sortOrder: Int = this.sortOrder,
        bankNaam: String? = this.bankNaam,
        vanPeriode: Periode? = this.vanPeriode,
        totEnMetPeriode: Periode? = this.totEnMetPeriode,
        budgetBedrag: BigDecimal? = this.budgetBedrag,
        budgetPeriodiciteit: BudgetPeriodiciteit? = this.budgetPeriodiciteit,
        budgetBetaalDag: Int? = this.budgetBetaalDag,
        betaalMethoden: List<Rekening> = this.betaalMethoden
    ) = Rekening(
        this.id,
        naam,
        rekeningGroep,
        sortOrder,
        bankNaam,
        vanPeriode,
        totEnMetPeriode,
        budgetBedrag,
        budgetPeriodiciteit,
        budgetBetaalDag,
        betaalMethoden
    )

    fun fromDTO(
        dto: RekeningDTO,
        rekeningGroep: RekeningGroep,
    ) = Rekening(
        dto.id,
        dto.naam,
        rekeningGroep,
        dto.sortOrder,
        dto.bankNaam,
        dto.vanPeriode,
        dto.totEnMetPeriode,
        dto.budgetBedrag,
        BudgetPeriodiciteit.valueOf(dto.budgetPeriodiciteit ?: BudgetPeriodiciteit.MAAND.name),
        dto.budgetBetaalDag
    )

    enum class BudgetPeriodiciteit {
        WEEK, MAAND
    }
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class RekeningDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningGroepNaam: String,
        val sortOrder: Int,
        val bankNaam: String? = null,
        val vanPeriode: Periode? = null,
        val totEnMetPeriode: Periode? = null,
        val budgetPeriodiciteit: String? = null,
        val budgetBedrag: BigDecimal? = null,
        val budgetBetaalDag: Int?,
        val betaalMethoden: List<RekeningDTO> = emptyList(),
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
            rekeningGroepNaam: String = this.rekeningGroepNaam,
            sortOrder: Int = this.sortOrder,
            bankNaam: String? = this.bankNaam,
            vanPeriode: Periode? = this.vanPeriode,
            totEnMetPeriode: Periode? = this.totEnMetPeriode,
            budgetPeriodiciteit: String? = this.budgetPeriodiciteit,
            budgetBedrag: BigDecimal? = this.budgetBedrag,
            budgetBetaalDag: Int? = this.budgetBetaalDag,
            betaalMethoden: List<RekeningDTO> = this.betaalMethoden,
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
            this.id,
            naam,
            rekeningGroepNaam,
            sortOrder,
            bankNaam,
            vanPeriode,
            totEnMetPeriode,
            budgetPeriodiciteit,
            budgetBedrag,
            budgetBetaalDag,
            betaalMethoden,
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
        periode: Periode? = null,
        budgetPeilDatum: String? = null,
        budgetOpPeilDatum: BigDecimal? = null,
        budgetBetaling: BigDecimal? = null,
        betaaldBinnenBudget: BigDecimal? = null,
        minderDanBudget: BigDecimal? = null,
        meerDanBudget: BigDecimal? = null,
        meerDanMaandBudget: BigDecimal? = null,
        restMaandBudget: BigDecimal? = null,
    ): RekeningDTO {
        val budgetMaandBedrag = if (periode != null && budgetBedrag != null) {
            if (budgetPeriodiciteit == BudgetPeriodiciteit.WEEK) {
                val periodeLengte = periode.periodeEindDatum.dayOfYear - periode.periodeStartDatum.dayOfYear + 1
                (budgetBedrag.times(BigDecimal(periodeLengte)).div(BigDecimal(7))).setScale(
                    2,
                    java.math.RoundingMode.HALF_UP
                )
            } else {
                budgetBedrag
            }
        } else null
        return RekeningDTO(
            this.id,
            this.naam,
            this.rekeningGroep.naam,
            this.sortOrder,
            this.bankNaam,
            this.vanPeriode,
            this.totEnMetPeriode,
            this.budgetPeriodiciteit?.name,
            this.budgetBedrag,
            this.budgetBetaalDag,
            this.betaalMethoden.map { it.toDTO(periode) },
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
