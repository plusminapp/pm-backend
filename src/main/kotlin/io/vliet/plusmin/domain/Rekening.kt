package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.vliet.plusmin.domain.Periode.Companion.berekenDagInPeriode
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "rekening",
    uniqueConstraints = [UniqueConstraint(columnNames = ["rekeningGroep_id", "naam"])]
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
    val sortOrder: Int, // volgorde binnen de rekeninggroep; de eerste wordt gebruikt om correcties op toe te passen
    val bankNaam: String? = null,
    @ManyToOne
    @JoinColumn(name = "van_periode_id")
    val vanPeriode: Periode? = null,
    @ManyToOne
    @JoinColumn(name = "tot_periode_id")
    val totEnMetPeriode: Periode? = null,
    @ManyToOne
    @JoinColumn(name = "gekoppelde_rekening_id")
    val gekoppeldeRekening: Rekening? = null, // gekoppelde spaarrekening bij spaarpotje of gekoppelde betaalrekening bij potje
    val budgetBedrag: BigDecimal? = null,
    val budgetVariabiliteit: Int? = 0, // toegestane afwijking in procenten om een betaling te accepteren bij vaste lasten en aflossingen
    @Column(name = "maanden")
    @Convert(converter = RekeningMaandenConverter::class)
    var maanden: Set<Int>? = null,
    @Enumerated(EnumType.STRING)
    val budgetPeriodiciteit: BudgetPeriodiciteit? = null,
    val budgetBetaalDag: Int? = null,
    @Enumerated(EnumType.STRING)
    val budgetAanvulling: BudgetAanvulling? = null,
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
    @OneToOne(optional = true)
    @JoinColumn(name = "spaartegoed_id", nullable = true)
    val spaartegoed: Spaartegoed? = null,
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
        gekoppeldeRekening: Rekening? = this.gekoppeldeRekening,
        budgetBedrag: BigDecimal? = this.budgetBedrag,
        budgetVariabiliteit: Int? = this.budgetVariabiliteit,
        maanden: Set<Int>? = this.maanden,
        budgetPeriodiciteit: BudgetPeriodiciteit? = this.budgetPeriodiciteit,
        budgetBetaalDag: Int? = this.budgetBetaalDag,
        budgetAanvulling: BudgetAanvulling? = this.budgetAanvulling,
        betaalMethoden: List<Rekening> = this.betaalMethoden,
        aflossing: Aflossing? = this.aflossing,
        spaartegoed: Spaartegoed? = this.spaartegoed,
    ) = Rekening(
        this.id,
        naam,
        rekeningGroep,
        sortOrder,
        bankNaam,
        vanPeriode,
        totEnMetPeriode,
        gekoppeldeRekening,
        budgetBedrag,
        budgetVariabiliteit,
        maanden,
        budgetPeriodiciteit,
        budgetBetaalDag,
        budgetAanvulling,
        betaalMethoden,
        aflossing,
        spaartegoed
    )

//    fun fromDTO(
//        rekeningDTO: RekeningDTO,
//        rekeningGroep: RekeningGroep,
//    ) = Rekening(
//        rekeningDTO.id,
//        rekeningDTO.naam,
//        rekeningGroep,
//        rekeningDTO.sortOrder ?: 0,
//        rekeningDTO.bankNaam,
//        rekeningDTO.vanPeriode,
//        rekeningDTO.totEnMetPeriode,
//        rekeningDTO.gekoppeldeRekening,
//        rekeningDTO.budgetBedrag,
//        rekeningDTO.budgetVariabiliteit,
//        rekeningDTO.maanden,
//        BudgetPeriodiciteit.valueOf(rekeningDTO.budgetPeriodiciteit ?: BudgetPeriodiciteit.MAAND.name),
//        rekeningDTO.budgetBetaalDag,
//        rekeningDTO.budgetAanvulling?.let { BudgetAanvulling.valueOf(it.name) },
//    )

    enum class BudgetPeriodiciteit {
        WEEK, MAAND
    }

    enum class BudgetAanvulling {
        TOT, MET, IN, UIT
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class RekeningDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningGroepNaam: String?,
        val sortOrder: Int? = 0,
        val bankNaam: String? = null,
        val vanPeriode: Periode? = null,
        val totEnMetPeriode: Periode? = null,
        val gekoppeldeRekening: String? = null,
        val budgetPeriodiciteit: String? = null,
        val saldo: BigDecimal? = null,
        val reserve: BigDecimal? = null,
        val budgetBedrag: BigDecimal? = null,
        val budgetVariabiliteit: Int? = null,
        val maanden: Set<Int>? = emptySet(),
        val budgetBetaalDag: Int? = null,
        val budgetAanvulling: BudgetAanvulling? = null,
        val betaalMethoden: List<RekeningDTO> = emptyList(),
        val budgetMaandBedrag: BigDecimal? = null,
        val aflossing: Aflossing.AflossingDTO? = null,
        val spaartegoed: Spaartegoed.SpaartegoedDTO? = null,
    ) {
        fun fullCopy(
            naam: String = this.naam,
            rekeningGroepNaam: String? = this.rekeningGroepNaam,
            sortOrder: Int? = this.sortOrder,
            bankNaam: String? = this.bankNaam,
            vanPeriode: Periode? = this.vanPeriode,
            totEnMetPeriode: Periode? = this.totEnMetPeriode,
            gekoppeldeRekening: String? = this.gekoppeldeRekening,
            budgetPeriodiciteit: String? = this.budgetPeriodiciteit,
            saldo: BigDecimal? = this.saldo,
            reserve: BigDecimal? = this.reserve,
            budgetBedrag: BigDecimal? = this.budgetBedrag,
            budgetBetaalDag: Int? = this.budgetBetaalDag,
            budgetAanvulling: BudgetAanvulling? = this.budgetAanvulling,
            betaalMethoden: List<RekeningDTO> = this.betaalMethoden,
            budgetMaandBedrag: BigDecimal? = this.budgetMaandBedrag,
            aflossing: Aflossing.AflossingDTO? = this.aflossing,
            spaartegoed: Spaartegoed.SpaartegoedDTO? = this.spaartegoed,
        ) = RekeningDTO(
            this.id,
            naam,
            rekeningGroepNaam,
            sortOrder,
            bankNaam,
            vanPeriode,
            totEnMetPeriode,
            gekoppeldeRekening,
            budgetPeriodiciteit,
            saldo,
            reserve,
            budgetBedrag,
            budgetVariabiliteit,
            maanden,
            budgetBetaalDag,
            budgetAanvulling,
            betaalMethoden,
            budgetMaandBedrag,
            aflossing,
            spaartegoed
        )
    }

    fun toDTO(
        periode: Periode? = null,
        betaling: BigDecimal? = null
    ): RekeningDTO {
        val budgetMaandBedrag =
            if (periode == null || !this.rekeningIsGeldigInPeriode(periode) || this.budgetBedrag == null)
                BigDecimal.ZERO
            else {
                val budgetBetaalDagInPeriode = if (this.budgetBetaalDag != null)
                    periode.berekenDagInPeriode(this.budgetBetaalDag) else null
                val wordtBetalingVerwacht =
                    this.maanden.isNullOrEmpty() || this.maanden!!.contains(budgetBetaalDagInPeriode?.monthValue)
                if (this.budgetPeriodiciteit == BudgetPeriodiciteit.WEEK) {
                    val periodeLengte = periode.periodeEindDatum.dayOfYear - periode.periodeStartDatum.dayOfYear + 1
                    (this.budgetBedrag.times(BigDecimal(periodeLengte))
                        .div(BigDecimal(7)))
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                } else if (!wordtBetalingVerwacht) {
                    BigDecimal.ZERO
                } else if (betaling == null) {
                    this.budgetBedrag
                } else { //er wordt een betaling verwacht en de betaling != null
                    val isBedragBinnenVariabiliteit =
                        isBedragBinnenVariabiliteit(betaling)
                    if (isBedragBinnenVariabiliteit) betaling.abs() else this.budgetBedrag
                }
            }
        return RekeningDTO(
            this.id,
            this.naam,
            this.rekeningGroep.naam,
            this.sortOrder,
            this.bankNaam,
            this.vanPeriode,
            this.totEnMetPeriode,
            this.gekoppeldeRekening?.naam,
            this.budgetPeriodiciteit?.name,
            null,
            null,
            this.budgetBedrag,
            this.budgetVariabiliteit,
            this.maanden,
            this.budgetBetaalDag,
            this.budgetAanvulling,
            this.betaalMethoden.map { it.toDTO() },
            budgetMaandBedrag,
            this.aflossing?.toDTO(),
            this.spaartegoed?.toDTO()
        )
    }

    fun rekeningIsGeldigInPeriode(periode: Periode): Boolean {
        return (this.vanPeriode == null || periode.periodeStartDatum >= this.vanPeriode.periodeStartDatum) &&
                (this.totEnMetPeriode == null || periode.periodeEindDatum <= this.totEnMetPeriode.periodeEindDatum) &&
                (periode.periodeStartDatum != periode.periodeEindDatum)
    }

    fun isBedragBinnenVariabiliteit(
        betaling: BigDecimal
    ): Boolean {
        return if (this.budgetBedrag == null) {
            true
        } else betaling.abs() <= this.budgetBedrag.times(
            BigDecimal(
                100 + (this.budgetVariabiliteit ?: 0)
            ).divide(BigDecimal(100))
        ) &&
                betaling.abs() >= this.budgetBedrag.times(
            BigDecimal(
                100 - (this.budgetVariabiliteit ?: 0)
            ).divide(BigDecimal(100))
        )
    }
}
