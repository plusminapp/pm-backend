package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.vliet.plusmin.domain.Periode.Companion.berekenDagInPeriode
import io.vliet.plusmin.domain.Rekening.BudgetAanvulling
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

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
    val openingsAchterstand: BigDecimal = BigDecimal.ZERO,          // achterstand aan het begin van de periode
    val periodeBetaling: BigDecimal = BigDecimal.ZERO,              // betaling deze periode
    val periodeReservering: BigDecimal = BigDecimal.ZERO,           // reservering deze periode
    val periodeAchterstand: BigDecimal = BigDecimal.ZERO,           // nieuwe/ingelopen achterstand saldo deze periode
    val budgetMaandBedrag: BigDecimal = BigDecimal.ZERO,            // verwachte bedrag per maand o.b.v. de periode lengte
    val correctieBoeking: BigDecimal = BigDecimal.ZERO,             // correctieBoeking om de eindsaldi kloppend te maken
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "periode_id", referencedColumnName = "id")
    var periode: Periode
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        openingsBalansSaldo: BigDecimal = this.openingsBalansSaldo,
        openingsReserveSaldo: BigDecimal = this.openingsReserveSaldo,
        openingsAchterstand: BigDecimal = this.openingsAchterstand,
        budgetMaandBedrag: BigDecimal = this.budgetMaandBedrag,
        periodeBetaling: BigDecimal = this.periodeBetaling,
        periodeReservering: BigDecimal = this.periodeReservering,
        periodeAchterstand: BigDecimal = this.periodeAchterstand,
        correctieBoeking: BigDecimal = this.correctieBoeking,
        periode: Periode = this.periode,
    ) = Saldo(
        this.id,
        rekening,
        openingsBalansSaldo,
        openingsReserveSaldo,
        openingsAchterstand,
        periodeBetaling,
        periodeReservering,
        periodeAchterstand,
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
        val spaarpot: Spaarpot.SpaartegoedDTO? = null,
        val sortOrder: Int = 0,
        val openingsBalansSaldo: BigDecimal = BigDecimal.ZERO,
        val openingsReserveSaldo: BigDecimal = BigDecimal.ZERO,
        val openingsAchterstand: BigDecimal = BigDecimal.ZERO,
        val peilDatum: String? = null,
        val budgetMaandBedrag: BigDecimal = BigDecimal.ZERO,
        val periodeBetaling: BigDecimal = BigDecimal.ZERO,
        val periodeReservering: BigDecimal = BigDecimal.ZERO,
        val correctieBoeking: BigDecimal = BigDecimal.ZERO,
        val periodeAchterstand: BigDecimal? = null,
        val budgetOpPeilDatum: BigDecimal? = null, // wat er verwacht betaald/ontvangen zou moeten zijn op de peildatum
        val betaaldBinnenBudget: BigDecimal? = null,
        val minderDanBudget: BigDecimal? = null,
        val meerDanBudget: BigDecimal? = null,
        val meerDanMaandBudget: BigDecimal? = null,
        val komtNogNodig: BigDecimal? = null,
    )

    fun toDTO(peilDatum: LocalDate = this.periode.periodeEindDatum): SaldoDTO {
        val budgetOpPeilDatum = berekenBudgetOpPeilDatum(
            this.rekening,
            peilDatum,
            this.budgetMaandBedrag,
            this.periodeBetaling,
            this.periode
        ) ?: BigDecimal.ZERO
        val budgetMaandBedrag = rekening.toDTO(periode).budgetMaandBedrag ?: BigDecimal.ZERO
        val komtNogNodig = if (this.rekening.rekeningGroep.budgetType == RekeningGroep.BudgetType.VAST) {
            budgetMaandBedrag - this.periodeBetaling
        } else {
            budgetMaandBedrag - budgetOpPeilDatum
        }

        val betaaldBinnenBudget = BigDecimal.ZERO
        val minderDanBudget = BigDecimal.ZERO
        val meerDanMaandBudget = BigDecimal.ZERO
        val meerDanBudget = BigDecimal.ZERO
        return SaldoDTO(
            this.rekening.id,
            this.rekening.rekeningGroep.naam,
            this.rekening.rekeningGroep.rekeningGroepSoort,
            this.rekening.rekeningGroep.budgetType,
            this.rekening.naam,
            this.rekening.budgetBetaalDag,
            this.rekening.budgetAanvulling,
            this.rekening.aflossing?.toDTO(),
            this.rekening.spaarpot?.toDTO(),
            1000 * this.rekening.rekeningGroep.sortOrder + this.rekening.sortOrder,
            this.openingsBalansSaldo,
            this.openingsReserveSaldo,
            this.openingsAchterstand,
            peilDatum.toString(),
            budgetMaandBedrag,
            this.periodeBetaling,
            this.periodeReservering,
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

    fun berekendBudgetOpPeilDatum(peilDatum: LocalDate): BigDecimal {
        val periodeStartDatum = this.periode.periodeStartDatum
        val maandenTussen = ((peilDatum.year - periodeStartDatum.year) * 12 + peilDatum.monthValue - periodeStartDatum.monthValue).coerceAtLeast(0)
        return this.budgetMaandBedrag.multiply(BigDecimal(maandenTussen))
    }

    fun berekenBudgetOpPeilDatum(
        rekening: Rekening,
        peilDatum: LocalDate,
        budgetMaandBedrag: BigDecimal,
        betaling: BigDecimal,
        peilPeriode: Periode
    ): BigDecimal? {
        val budgetOpPeilDatum =
            when (rekening.rekeningGroep.budgetType) {
                RekeningGroep.BudgetType.VAST, RekeningGroep.BudgetType.INKOMSTEN -> {
                    berekenVastBudgetOpPeildatum(
                        rekening,
                        peilPeriode,
                        rekening.rekeningGroep.budgetType,
                        peilDatum,
                        budgetMaandBedrag,
                        betaling
                    )
                }

                RekeningGroep.BudgetType.CONTINU -> {
                    berekenContinuBudgetOpPeildatum(rekening, peilPeriode, peilDatum)
                }

                else -> BigDecimal.ZERO
            }
        return budgetOpPeilDatum
    }

    private fun berekenVastBudgetOpPeildatum(
        rekening: Rekening,
        peilPeriode: Periode,
        budgetType: RekeningGroep.BudgetType,
        peilDatum: LocalDate,
        budgetMaandBedrag: BigDecimal,
        betaling: BigDecimal
    ): BigDecimal? {
        val betaaldagInPeriode = if (rekening.budgetBetaalDag != null)
            peilPeriode.berekenDagInPeriode(rekening.budgetBetaalDag)
        else null

        if (betaaldagInPeriode == null) {
            throw PM_GeenBetaaldagException(
                listOf(
                    rekening.naam,
                    budgetType.name,
                    rekening.rekeningGroep.administratie.naam
                )
            )
        }
        return if (!peilDatum.isBefore(betaaldagInPeriode)) budgetMaandBedrag
        else (budgetMaandBedrag).min(betaling.abs())
    }

    fun berekenContinuBudgetOpPeildatum(
        rekening: Rekening,
        gekozenPeriode: Periode,
        peilDatum: LocalDate
    ): BigDecimal {
        if (peilDatum < gekozenPeriode.periodeStartDatum) return BigDecimal.ZERO
        val dagenInPeriode: Long =
            gekozenPeriode.periodeEindDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        val budgetMaandBedrag = when (rekening.budgetPeriodiciteit) {
            Rekening.BudgetPeriodiciteit.WEEK -> rekening.budgetBedrag
                ?.times(BigDecimal(dagenInPeriode))
                ?.div(BigDecimal(7)) ?: BigDecimal.ZERO

            Rekening.BudgetPeriodiciteit.MAAND -> rekening.budgetBedrag ?: BigDecimal.ZERO
            null -> BigDecimal.ZERO
        }
        if (peilDatum >= gekozenPeriode.periodeEindDatum) return budgetMaandBedrag
        val dagenTotPeilDatum: Long = peilDatum.toEpochDay() - gekozenPeriode.periodeStartDatum.toEpochDay() + 1
        return (budgetMaandBedrag.times(BigDecimal(dagenTotPeilDatum)).div(BigDecimal(dagenInPeriode)))
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
