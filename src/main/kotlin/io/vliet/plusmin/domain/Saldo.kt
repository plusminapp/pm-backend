package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.vliet.plusmin.domain.Rekening.BudgetAanvulling
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
    val openingsBalansSaldo: BigDecimal = BigDecimal.ZERO,           //saldo aan het begin van de periode
    val openingsReserveSaldo: BigDecimal = BigDecimal.ZERO,           //saldo aan het begin van de periode
    val achterstand: BigDecimal = BigDecimal.ZERO,            // achterstand aan het begin van de periode
    val budgetMaandBedrag: BigDecimal = BigDecimal.ZERO,      // verwachte bedrag per maand
    val betaling: BigDecimal = BigDecimal.ZERO,         // betaling deze periode
    val reservering: BigDecimal = BigDecimal.ZERO,         // reservering deze periode
    val oorspronkelijkeBetaling: BigDecimal = BigDecimal.ZERO,         // betaling deze periode
    val budgetVariabiliteit: Int? = null,                        // variabiliteit als percentage van budgetMaandBedrag
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "periode_id", referencedColumnName = "id")
    var periode: Periode? = null
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        openingsBalansSaldo: BigDecimal = this.openingsBalansSaldo,
        openingsReserveSaldo: BigDecimal = this.openingsReserveSaldo,
        achterstand: BigDecimal = this.achterstand,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        betaling: BigDecimal = this.betaling,
        reservering: BigDecimal = this.reservering,
        oorspronkelijkeBetaling: BigDecimal = this.oorspronkelijkeBetaling,
        budgetVariabiliteit: Int? = this.budgetVariabiliteit,
        periode: Periode? = this.periode,
    ) = Saldo(
        this.id,
        rekening,
        openingsBalansSaldo,
        openingsReserveSaldo,
        achterstand,
        budgetMaandBedrag,
        betaling,
        reservering,
        oorspronkelijkeBetaling,
        budgetVariabiliteit,
        periode
    )

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class SaldoDTO(
        val id: Long = 0,
        val rekeningGroepNaam: String = "",
        val rekeningGroepSoort: RekeningGroep.RekeningGroepSoort? = null,
        val budgetType: RekeningGroep.BudgetType? = null,
        val rekeningNaam: String,
        val aflossing: Aflossing.AflossingDTO? = null,
        val spaartegoed: Spaartegoed.SpaartegoedDTO? = null,
        val sortOrder: Int = 0,
        val openingsBalansSaldo: BigDecimal = BigDecimal.ZERO,
        val openingsReserveSaldo: BigDecimal = BigDecimal.ZERO,
        val achterstand: BigDecimal = BigDecimal.ZERO,
        val budgetMaandBedrag: BigDecimal = BigDecimal.ZERO,
        val budgetBetaalDag: Int? = null,
        val budgetAanvulling: BudgetAanvulling? = null,
        val betaling: BigDecimal = BigDecimal.ZERO,
        val reservering: BigDecimal = BigDecimal.ZERO,
        val oorspronkelijkeBetaling: BigDecimal = BigDecimal.ZERO,
        val achterstandOpPeilDatum: BigDecimal? = null,
        val budgetPeilDatum: String? = null,
        val budgetOpPeilDatum: BigDecimal? = null, // wat er verwacht betaald zou moeten zijn op de peildatum
        // invarianten:
        // * budgetMaandBedrag = betaaldBinnenBudget + minderDanBudget + restMaandBudget
        // * budgetOpPeilDatum = betaling - meerDanBudget - meerDanMaandBudget
        val betaaldBinnenBudget: BigDecimal? = null,
        val minderDanBudget: BigDecimal? = null,
        val meerDanBudget: BigDecimal? = null,
        val meerDanMaandBudget: BigDecimal? = null,
        val restMaandBudget: BigDecimal? = null,
    )

    fun toDTO(
    ): SaldoDTO {
        // Saldo -> SaldoDTO kan alleen voor periodes die zijn afgelopen
        val achterstandOpPeilDatum = this.achterstand + this.betaling.abs() - this.budgetMaandBedrag
        val budgetPeilDatum = periode?.periodeEindDatum.toString()
        val budgetOpPeilDatum = this.budgetMaandBedrag.abs()
        val betaaldBinnenBudget = this.betaling.abs().min(this.budgetMaandBedrag)
        val minderDanBudget = BigDecimal.ZERO
        val meerDanMaandBudget = BigDecimal.ZERO
        val meerDanBudget = BigDecimal.ZERO
        val restMaandBudget = BigDecimal.ZERO
        val openingsBalansSaldo = this.openingsBalansSaldo
        val openingsReserveSaldo = this.openingsReserveSaldo
        return SaldoDTO(
            this.id,
            this.rekening.rekeningGroep.naam,
            this.rekening.rekeningGroep.rekeningGroepSoort,
            this.rekening.rekeningGroep.budgetType,
            this.rekening.naam,
            this.rekening.aflossing?.toDTO(),
            this.rekening.spaartegoed?.toDTO(),
            1000 * this.rekening.rekeningGroep.sortOrder + this.rekening.sortOrder,
            openingsBalansSaldo,
            openingsReserveSaldo,
            this.achterstand,
            this.budgetMaandBedrag,
            this.rekening.budgetBetaalDag,
            this.rekening.budgetAanvulling,
            this.betaling,
            this.reservering,
            this.oorspronkelijkeBetaling,
            achterstandOpPeilDatum,
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
        val openingsReservePotjesVoorNuSaldo: BigDecimal,
        val budgetMaandInkomstenBedrag: BigDecimal,
        val besteedTotPeilDatum: BigDecimal,
        val gespaardTotPeilDatum: BigDecimal,
        val nogNodigNaPeilDatum: BigDecimal,
        val actueleBuffer: BigDecimal,
        val extraGespaardTotPeilDatum: BigDecimal,
    )
}
