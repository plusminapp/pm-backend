//package io.vliet.plusmin.domain
//
//import com.fasterxml.jackson.annotation.JsonIgnore
//import jakarta.persistence.*
//import java.math.BigDecimal
//
//@Entity
//@Table(
//    name = "budget",
//    uniqueConstraints = [UniqueConstraint(columnNames = ["rekening", "naam"])]
//)
//class Budget(
//    @Id
//    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
//    @SequenceGenerator(
//        name = "hibernate_sequence",
//        sequenceName = "hibernate_sequence",
//        allocationSize = 1
//    )
//    val id: Long = 0,
//    @ManyToOne
//    @JsonIgnore
//    @JoinColumn(name = "rekening_id", nullable = false)
//    val rekening: Rekening,
//    val budgetNaam: String,
//    val budgetPeriodiciteit: Rekening.BudgetPeriodiciteit = Rekening.BudgetPeriodiciteit.MAAND,
//    val bedrag: BigDecimal,
//    val betaalDag: Int?,
//    @ManyToOne
//    @JoinColumn(name = "van_periode_id")
//    val vanPeriode: Periode? = null,
//    @ManyToOne
//    @JoinColumn(name = "tot_periode_id")
//    val totEnMetPeriode: Periode? = null,
//) {
//    fun fullCopy(
//        rekening: Rekening = this.rekening,
//        budgetNaam: String = this.budgetNaam,
//        bedrag: BigDecimal = this.bedrag,
//        budgetPeriodiciteit: Rekening.BudgetPeriodiciteit = this.budgetPeriodiciteit,
//        betaalDag: Int? = this.betaalDag,
//        vanPeriode: Periode? = this.vanPeriode,
//        totEnMetPeriode: Periode? = this.totEnMetPeriode,
//    ) = Budget(this.id, rekening, budgetNaam, budgetPeriodiciteit, bedrag, betaalDag, vanPeriode, totEnMetPeriode)
//
//    data class BudgetDTO(
//        val id: Long = 0,
//        val rekeningNaam: String,
//        val rekeningSoort: String? = null,
//        val budgetNaam: String,
//        val budgetType: String,
//        val budgetPeriodiciteit: String,
//        val bedrag: BigDecimal,
//        val betaalDag: Int?,
//        val vanPeriode: Periode? = null,
//        val totEnMetPeriode: Periode? = null,
//        val budgetMaandBedrag: BigDecimal? = null,
//        val budgetPeilDatum: String? = null,
//        val budgetOpPeilDatum: BigDecimal? = null,
//        val budgetBetaling: BigDecimal? = null,
//        val betaaldBinnenBudget: BigDecimal? = null,
//        val minderDanBudget: BigDecimal? = null,
//        val meerDanBudget: BigDecimal? = null,
//        val meerDanMaandBudget: BigDecimal? = null,
//        val restMaandBudget: BigDecimal? = null,
//    ) {
//        fun fullCopy(
//            rekeningNaam: String = this.rekeningNaam,
//            rekeningSoort: String? = this.rekeningSoort,
//            budgetNaam: String = this.budgetNaam,
//            budgetType: String = this.budgetType,
//            budgetPeriodiciteit: String = this.budgetPeriodiciteit,
//            bedrag: BigDecimal = this.bedrag,
//            betaalDag: Int? = this.betaalDag,
//            vanPeriode: Periode? = this.vanPeriode,
//            totEnMetPeriode: Periode? = this.totEnMetPeriode,
//            budgetMaandBedrag: BigDecimal? = this.budgetMaandBedrag,
//            budgetPeilDatum: String? = this.budgetPeilDatum,
//            budgetOpPeilDatum: BigDecimal? = this.budgetOpPeilDatum,
//            budgetBetaling: BigDecimal? = this.budgetBetaling,
//            betaaldBinnenBudget: BigDecimal? = this.betaaldBinnenBudget,
//            minderDanBudget: BigDecimal? = this.minderDanBudget,
//            meerDanBudget: BigDecimal? = this.meerDanBudget,
//            meerDanMaandBudget: BigDecimal? = this.meerDanMaandBudget,
//            restMaandBudget: BigDecimal? = this.restMaandBudget,
//        ): BudgetDTO = BudgetDTO(
//            this.id,
//            rekeningNaam,
//            rekeningSoort,
//            budgetNaam,
//            budgetType,
//            budgetPeriodiciteit,
//            bedrag,
//            betaalDag,
//            vanPeriode,
//            totEnMetPeriode,
//            budgetMaandBedrag,
//            budgetPeilDatum,
//            budgetOpPeilDatum,
//            budgetBetaling,
//            betaaldBinnenBudget,
//            minderDanBudget,
//            meerDanBudget,
//            meerDanMaandBudget,
//            restMaandBudget,
//        )
//    }
//
//    fun toDTO(
//        budgetMaandBedrag: BigDecimal? = null,
//        budgetPeilDatum: String? = null,
//        budgetOpPeilDatum: BigDecimal? = null,
//        budgetBetaling: BigDecimal? = null,
//        betaaldBinnenBudget: BigDecimal? = null,
//        minderDanBudget: BigDecimal? = null,
//        meerDanBudget: BigDecimal? = null,
//        meerDanMaandBudget: BigDecimal? = null,
//        restMaandBudget: BigDecimal? = null,
//    ): BudgetDTO {
//        return BudgetDTO(
//            this.id,
//            this.rekening.naam,
//            this.rekening.rekeningGroep.rekeningGroepSoort.toString(),
//            this.budgetNaam,
//            this.rekening.rekeningGroep.budgetType.toString(),
//            this.budgetPeriodiciteit.toString(),
//            this.bedrag,
//            this.betaalDag,
//            this.vanPeriode,
//            this.totEnMetPeriode,
//            budgetMaandBedrag,
//            budgetPeilDatum,
//            budgetOpPeilDatum,
//            budgetBetaling,
//            betaaldBinnenBudget,
//            minderDanBudget,
//            meerDanBudget,
//            meerDanMaandBudget,
//            restMaandBudget,
//        )
//    }
//
//    data class BudgetSamenvattingDTO(
//        val percentagePeriodeVoorbij: Long,
//        val budgetMaandInkomstenBedrag: BigDecimal,
//        val besteedTotPeilDatum: BigDecimal,
//        val nogNodigNaPeilDatum: BigDecimal,
//        val actueleBuffer: BigDecimal,
//    )
//}
