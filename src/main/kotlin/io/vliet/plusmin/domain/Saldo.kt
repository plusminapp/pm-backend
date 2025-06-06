package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal

/*
    De Saldo tabel bevat het saldo van een rekening; door de relatie naar de Periode tabel
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
    @JoinColumn(name = "rekening_groep_id", referencedColumnName = "id")
    val rekeningGroep: RekeningGroep,
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
        rekeningGroep: RekeningGroep = this.rekeningGroep,
        rekening: Rekening = this.rekening,
        saldo: BigDecimal = this.saldo,
        achterstand: BigDecimal = this.achterstand,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        budgetBetaling: BigDecimal = this.budgetBetaling,
        periode: Periode? = this.periode,
    ) = Saldo(this.id, rekeningGroep, rekening, saldo, achterstand, budgetMaandBedrag, budgetBetaling, periode)

    data class SaldoDTO(
        val id: Long = 0,
        val rekeningGroepNaam: String? = "",
        val rekeningGroepSoort: RekeningGroep.RekeningGroepSoort? = null,
        val budgetType: RekeningGroep.BudgetType? = null,
        val rekeningNaam: String,
        val saldo: BigDecimal = BigDecimal(0),
        val achterstand: BigDecimal = BigDecimal(0),
        val budgetMaandBedrag: BigDecimal = BigDecimal(0),
        val budgetBetaling: BigDecimal = BigDecimal(0),
        val periode: Periode? = null,
        val budgetPeilDatum: String? = null,
        val budgetOpPeilDatum: BigDecimal? = null,
        val betaaldBinnenBudget: BigDecimal? = null,
        val minderDanBudget: BigDecimal? = null,
        val meerDanBudget: BigDecimal? = null,
        val meerDanMaandBudget: BigDecimal? = null,
        val restMaandBudget: BigDecimal? = null,
    )

    fun toBalansDTO(
        periode: Periode? = this.periode,
        budgetPeilDatum: String? = null,
        budgetOpPeilDatum: BigDecimal? = null,
        betaaldBinnenBudget: BigDecimal? = null,
        minderDanBudget: BigDecimal? = null,
        meerDanBudget: BigDecimal? = null,
        meerDanMaandBudget: BigDecimal? = null,
        restMaandBudget: BigDecimal? = null,
    ): SaldoDTO {
        return SaldoDTO(
            this.id,
            this.rekening.rekeningGroep.naam,
            this.rekening.rekeningGroep.rekeningGroepSoort,
            this.rekening.rekeningGroep.budgetType,
            this.rekening.naam,
            this.saldo,
            this.achterstand,
            this.budgetMaandBedrag,
            this.budgetBetaling,
            periode,
            budgetPeilDatum,
            budgetOpPeilDatum,
            betaaldBinnenBudget,
            minderDanBudget,
            meerDanBudget,
            meerDanMaandBudget,
            restMaandBudget,
        )
    }

    fun toResultaatDTO(
        periode: Periode? = this.periode,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        budgetPeilDatum: String? = null,
        budgetOpPeilDatum: BigDecimal? = null,
        betaaldBinnenBudget: BigDecimal? = null,
        minderDanBudget: BigDecimal? = null,
        meerDanBudget: BigDecimal? = null,
        meerDanMaandBudget: BigDecimal? = null,
        restMaandBudget: BigDecimal? = null,
    ): SaldoDTO {
        return SaldoDTO(
            this.id,
            this.rekening.rekeningGroep.naam,
            this.rekening.rekeningGroep.rekeningGroepSoort,
            this.rekening.rekeningGroep.budgetType,
            this.rekening.naam,
            -this.saldo,
            this.achterstand,
            budgetMaandBedrag,
            this.budgetBetaling,
            periode,
            budgetPeilDatum,
            budgetOpPeilDatum,
            betaaldBinnenBudget,
            minderDanBudget,
            meerDanBudget,
            meerDanMaandBudget,
            restMaandBudget,
        )
    }
    data class ResultaatSamenvattingOpDatumDTO(
        val percentagePeriodeVoorbij: Long,
        val budgetMaandInkomstenBedrag: BigDecimal,
        val besteedTotPeilDatum: BigDecimal,
        val nogNodigNaPeilDatum: BigDecimal,
        val actueleBuffer: BigDecimal,
    )
}
