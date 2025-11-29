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
    val rekening: Rekening,                                         // bevat de betaaldag en de rekeningGroep
    val openingsBalansSaldo: BigDecimal = BigDecimal.ZERO,          // saldo aan het begin van de periode
    val openingsReserveSaldo: BigDecimal = BigDecimal.ZERO,         // reserve aan het begin van de periode
    val openingsOpgenomenSaldo: BigDecimal = BigDecimal.ZERO,       // opgenomen saldo  aan het begin van de periode
    val openingsAchterstand: BigDecimal = BigDecimal.ZERO,          // achterstand aan het begin van de periode
    val periodeBetaling: BigDecimal = BigDecimal.ZERO,              // betaling deze periode
    val periodeReservering: BigDecimal = BigDecimal.ZERO,           // reservering deze periode
    val periodeOpgenomenSaldo: BigDecimal = BigDecimal.ZERO,        // opgenomen saldo deze periode
    val periodeAchterstand: BigDecimal = BigDecimal.ZERO,           // nieuwe/ingelopen achterstand saldo deze periode
    val budgetMaandBedrag: BigDecimal = BigDecimal.ZERO,            // verwachte bedrag per maand obv de periode lengte
    val correctieBoeking: BigDecimal = BigDecimal.ZERO,             // correctieBoeking om de eindsaldi kloppend te maken
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "periode_id", referencedColumnName = "id")
    var periode: Periode? = null
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        openingsBalansSaldo: BigDecimal = this.openingsBalansSaldo,
        openingsReserveSaldo: BigDecimal = this.openingsReserveSaldo,
        openingsOpgenomenSaldo: BigDecimal = this.openingsOpgenomenSaldo,
        openingsAchterstand: BigDecimal = this.openingsAchterstand,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        periodeBetaling: BigDecimal = this.periodeBetaling,
        periodeReservering: BigDecimal = this.periodeReservering,
        periodeOpgenomenSaldo: BigDecimal = this.periodeOpgenomenSaldo,
        correctieBoeking: BigDecimal = this.correctieBoeking,
        periode: Periode? = this.periode,
    ) = Saldo(
        this.id,
        rekening,
        openingsBalansSaldo,
        openingsReserveSaldo,
        openingsOpgenomenSaldo,
        openingsAchterstand,
        periodeBetaling,
        periodeReservering,
        periodeOpgenomenSaldo,
        this@Saldo.periodeAchterstand,
        budgetMaandBedrag,
        correctieBoeking,
        periode
    )

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class SaldoDTO(
        val id: Long = 0,
        val rekeningGroepNaam: String = "",
        val rekeningGroepSoort: RekeningGroep.RekeningGroepSoort? = null,
        val budgetType: RekeningGroep.BudgetType? = null,
        val rekeningNaam: String,
        val budgetBetaalDag: Int? = null,
        val budgetAanvulling: BudgetAanvulling? = null,
        val aflossing: Aflossing.AflossingDTO? = null,
        val spaartegoed: Spaartegoed.SpaartegoedDTO? = null,
        val sortOrder: Int = 0,
        val openingsBalansSaldo: BigDecimal = BigDecimal.ZERO,
        val openingsReserveSaldo: BigDecimal = BigDecimal.ZERO,
        val openingsOpgenomenSaldo: BigDecimal = BigDecimal.ZERO,
        val openingsAchterstand: BigDecimal = BigDecimal.ZERO,
        val peilDatum: String? = null,
        val budgetMaandBedrag: BigDecimal = BigDecimal.ZERO,
        val periodeBetaling: BigDecimal = BigDecimal.ZERO,
        val periodeReservering: BigDecimal = BigDecimal.ZERO,
        val periodeOpgenomenSaldo: BigDecimal = BigDecimal.ZERO,
        val correctieBoeking: BigDecimal = BigDecimal.ZERO,
        val periodeAchterstand: BigDecimal? = null,
        val budgetOpPeilDatum: BigDecimal? = null, // wat er verwacht betaald zou moeten zijn op de peildatum
        val betaaldBinnenBudget: BigDecimal? = null,
        val minderDanBudget: BigDecimal? = null,
        val meerDanBudget: BigDecimal? = null,
        val meerDanMaandBudget: BigDecimal? = null,
        val komtNogNodig: BigDecimal? = null,
    )

    fun toDTO(): SaldoDTO {
//         Saldo → SaldoDTO kan alleen voor periodes die zijn afgelopen
        val peilDatum = periode?.periodeEindDatum.toString() // TODO wijzigen naar echte peildatum
        val budgetOpPeilDatum = this.budgetMaandBedrag.abs() // TODO berekenen obv echte peildatum
        val betaaldBinnenBudget = this.periodeBetaling.abs().min(this.budgetMaandBedrag)
        val minderDanBudget = BigDecimal.ZERO
        val meerDanMaandBudget = BigDecimal.ZERO
        val meerDanBudget = BigDecimal.ZERO
        val komtNogNodig = BigDecimal.ZERO
        return SaldoDTO(
            this.id,
            this.rekening.rekeningGroep.naam,
            this.rekening.rekeningGroep.rekeningGroepSoort,
            this.rekening.rekeningGroep.budgetType,
            this.rekening.naam,
            this.rekening.budgetBetaalDag,
            this.rekening.budgetAanvulling,
            this.rekening.aflossing?.toDTO(),
            this.rekening.spaartegoed?.toDTO(),
            1000 * this.rekening.rekeningGroep.sortOrder + this.rekening.sortOrder,
            this.openingsBalansSaldo,
            this.openingsReserveSaldo,
            this.openingsOpgenomenSaldo,
            this.openingsAchterstand,
            peilDatum,
            this.budgetMaandBedrag,
            this.periodeBetaling,
            this.periodeReservering,
            this.periodeOpgenomenSaldo,
            this.correctieBoeking,
            this.periodeAchterstand,
            budgetOpPeilDatum,
            betaaldBinnenBudget,
            minderDanBudget,
            meerDanBudget,
            meerDanMaandBudget,
            komtNogNodig,
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
