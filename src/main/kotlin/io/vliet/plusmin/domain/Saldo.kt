package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.*
import java.math.BigDecimal

/*
    De Saldo tabel bevat het saldo van een rekening; door de relatie naar de Periode tabel
    is het van 1 gebruiker, op 1 moment in de tijd.
    Het saldo van een gesloten periode bevat alle benodigde informatie over de stand van
    de rekening aan het einde van de periode.
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
    @JsonIgnore
    @JoinColumn(name = "rekening_id", referencedColumnName = "id")
    val rekening: Rekening,                                       // bevat de betaaldag en de rekeningGroep
    val openingsSaldo: BigDecimal = BigDecimal(0),           //saldo aan het begin van de periode
    val achterstand: BigDecimal = BigDecimal(0),            // achterstand aan het begin van de periode
    val budgetMaandBedrag: BigDecimal = BigDecimal(0),      // verwachte bedrag per maand
    val budgetBetaling: BigDecimal = BigDecimal(0),         // betaling deze periode
    val budgetVariabiliteit: Int? = null,                        // variabiliteit als percentage van budgetMaandBedrag
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "periode_id", referencedColumnName = "id")
    var periode: Periode? = null
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        openingsSaldo: BigDecimal = this.openingsSaldo,
        achterstand: BigDecimal = this.achterstand,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        budgetBetaling: BigDecimal = this.budgetBetaling,
        budgetVariabiliteit: Int? = this.budgetVariabiliteit,
        periode: Periode? = this.periode,
    ) = Saldo(this.id, rekening, openingsSaldo, achterstand, budgetMaandBedrag, budgetBetaling, budgetVariabiliteit, periode)

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class SaldoDTO(
        val id: Long = 0,
        val rekeningGroepNaam: String = "",
        val rekeningGroepSoort: RekeningGroep.RekeningGroepSoort? = null,
        val budgetType: RekeningGroep.BudgetType? = null,
        val rekeningNaam: String,
        val sortOrder: Int = 0,
        val openingsSaldo: BigDecimal = BigDecimal(0),
        val achterstand: BigDecimal = BigDecimal(0),
        val budgetMaandBedrag: BigDecimal = BigDecimal(0),
        val budgetBetaling: BigDecimal = BigDecimal(0),
        val achterstandNu: BigDecimal? = null,
        val budgetPeilDatum: String? = null,
        val budgetOpPeilDatum: BigDecimal? = null, // wat er verwacht betaald zou moeten zijn op de peildatum
        val betaaldBinnenBudget: BigDecimal? = null,
        val minderDanBudget: BigDecimal? = null,
        val meerDanBudget: BigDecimal? = null,
        val meerDanMaandBudget: BigDecimal? = null,
        val restMaandBudget: BigDecimal? = null,
    )

    fun toDTO(
    ): SaldoDTO {
        val achterstandNu = this.achterstand
        val budgetPeilDatum = periode?.periodeEindDatum.toString()
        val budgetOpPeilDatum = this.budgetMaandBedrag.abs()
        val betaaldBinnenBudget = this.budgetBetaling.abs().min(this.budgetMaandBedrag)
        val minderDanBudget = BigDecimal(0).max(this.budgetMaandBedrag.minus(this.budgetBetaling.abs()))
        val meerDanMaandBudget =  BigDecimal(0).max(this.budgetBetaling.abs() - this.budgetMaandBedrag)
        val meerDanBudget = BigDecimal(0).max(this.budgetBetaling.abs() - this.budgetMaandBedrag - meerDanMaandBudget)
        val restMaandBudget = BigDecimal(0)
        val openingsSaldo = if (RekeningGroep.balansRekeningGroepSoort.contains(this.rekening.rekeningGroep.rekeningGroepSoort)) {
            this.openingsSaldo
        } else {
            -this.openingsSaldo
        }
        return SaldoDTO(
            this.id,
            this.rekening.rekeningGroep.naam,
            this.rekening.rekeningGroep.rekeningGroepSoort,
            this.rekening.rekeningGroep.budgetType,
            this.rekening.naam,
            1000 * this.rekening.rekeningGroep.sortOrder + this.rekening.sortOrder,
            openingsSaldo,
            this.achterstand,
            this.budgetMaandBedrag,
            this.budgetBetaling,
            achterstandNu,
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
