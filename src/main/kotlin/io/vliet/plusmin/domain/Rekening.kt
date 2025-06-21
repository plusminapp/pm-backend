package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "rekening",
    uniqueConstraints = [UniqueConstraint(columnNames = ["rekeningGroep_id","naam"])]
)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    val budgetVariabiliteit: Int? = 0, // toegestane afwijking in procenten om een betaling te accepteren bij vaste lasten en aflossingen
    @Column(name = "maanden")
    @Convert(converter = RekeningMaandenConverter::class)
    var maanden: Set<Int>? = null,
    @Enumerated(EnumType.STRING)
    val budgetPeriodiciteit: BudgetPeriodiciteit? = null,
    val budgetBetaalDag: Int? = null,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "rekening_betaal_methoden",
        joinColumns = [JoinColumn(name = "rekening_id")],
        inverseJoinColumns = [JoinColumn(name = "betaalmethode_id")]
    )
    var betaalMethoden: List<Rekening> = emptyList(),
    @OneToOne(optional = true)
    @JoinColumn(name = "aflossing_id", nullable = true)
    val aflossing: Aflossing? = null,
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
        budgetVariabiliteit: Int? = this.budgetVariabiliteit,
        maanden: Set<Int>? = this.maanden,
        budgetPeriodiciteit: BudgetPeriodiciteit? = this.budgetPeriodiciteit,
        budgetBetaalDag: Int? = this.budgetBetaalDag,
        betaalMethoden: List<Rekening> = this.betaalMethoden,
        aflossing: Aflossing? = this.aflossing,
    ) = Rekening(
        this.id,
        naam,
        rekeningGroep,
        sortOrder,
        bankNaam,
        vanPeriode,
        totEnMetPeriode,
        budgetBedrag,
        budgetVariabiliteit,
        maanden,
        budgetPeriodiciteit,
        budgetBetaalDag,
        betaalMethoden,
        aflossing
    )

    fun fromDTO(
        rekeningDTO: RekeningDTO,
        rekeningGroep: RekeningGroep,
    ) = Rekening(
        rekeningDTO.id,
        rekeningDTO.naam,
        rekeningGroep,
        rekeningDTO.sortOrder,
        rekeningDTO.bankNaam,
        rekeningDTO.vanPeriode,
        rekeningDTO.totEnMetPeriode,
        rekeningDTO.budgetBedrag,
        rekeningDTO.budgetVariabiliteit,
        rekeningDTO.maanden,
        BudgetPeriodiciteit.valueOf(rekeningDTO.budgetPeriodiciteit ?: BudgetPeriodiciteit.MAAND.name),
        rekeningDTO.budgetBetaalDag
    )

    enum class BudgetPeriodiciteit {
        WEEK, MAAND
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class RekeningDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningGroepNaam: String?,
        val sortOrder: Int,
        val bankNaam: String? = null,
        val vanPeriode: Periode? = null,
        val totEnMetPeriode: Periode? = null,
        val budgetPeriodiciteit: String? = null,
        val saldo: BigDecimal? = null,
        val budgetBedrag: BigDecimal? = null,
        val budgetVariabiliteit: Int? = null,
        val maanden: Set<Int>? = emptySet(),
        val budgetBetaalDag: Int?,
        val betaalMethoden: List<RekeningDTO> = emptyList(),
        val budgetMaandBedrag: BigDecimal? = null,
        val aflossing: Aflossing.AflossingDTO? = null,
    ) {
        fun fullCopy(
            naam: String = this.naam,
            rekeningGroepNaam: String? = this.rekeningGroepNaam,
            sortOrder: Int = this.sortOrder,
            bankNaam: String? = this.bankNaam,
            vanPeriode: Periode? = this.vanPeriode,
            totEnMetPeriode: Periode? = this.totEnMetPeriode,
            budgetPeriodiciteit: String? = this.budgetPeriodiciteit,
            saldo: BigDecimal? = this.saldo,
            budgetBedrag: BigDecimal? = this.budgetBedrag,
            budgetBetaalDag: Int? = this.budgetBetaalDag,
            betaalMethoden: List<RekeningDTO> = this.betaalMethoden,
            budgetMaandBedrag: BigDecimal? = this.budgetMaandBedrag,
            aflossing: Aflossing.AflossingDTO? = this.aflossing,
        ) = RekeningDTO(
            this.id,
            naam,
            rekeningGroepNaam,
            sortOrder,
            bankNaam,
            vanPeriode,
            totEnMetPeriode,
            budgetPeriodiciteit,
            saldo,
            budgetBedrag,
            budgetVariabiliteit,
            maanden,
            budgetBetaalDag,
            betaalMethoden,
            budgetMaandBedrag,
            aflossing
        )
    }

    fun toDTO(
        periode: Periode? = null,
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
            null,
            this.budgetBedrag,
            this.budgetVariabiliteit,
            this.maanden,
            this.budgetBetaalDag,
            this.betaalMethoden.map { it.toDTO(periode) },
            budgetMaandBedrag,
            this.aflossing?.toDTO()
        )
    }
    fun rekeningIsGeldigInPeriode(periode: Periode): Boolean {
        return (this.vanPeriode == null || periode.periodeStartDatum >= this.vanPeriode.periodeStartDatum) &&
                (this.totEnMetPeriode == null || periode.periodeEindDatum <= this.totEnMetPeriode.periodeEindDatum)
    }
}
