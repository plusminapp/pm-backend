package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.*

@Entity
@Table(
    name = "rekening_groep",
    uniqueConstraints = [UniqueConstraint(columnNames = ["administratie_id", "naam"])]
)
@JsonInclude( JsonInclude.Include.NON_EMPTY)
class RekeningGroep(
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
    @JoinColumn(name = "administratie_id")
    val administratie: Administratie,
    @Enumerated(EnumType.STRING)
    val rekeningGroepSoort: RekeningGroepSoort,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val rekeningGroepIcoonNaam: String? = null,
    val sortOrder: Int,
    @Enumerated(EnumType.STRING)
    val budgetType: BudgetType? = null,
    @OneToMany(mappedBy = "rekeningGroep", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var rekeningen: List<Rekening> = emptyList()
) {
    companion object {
        val sortableFields = setOf("id", "naam", "afkorting")
        val resultaatRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.INKOMSTEN,
            RekeningGroepSoort.UITGAVEN,
        )
        val potjesVoorNuRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.UITGAVEN,
            RekeningGroepSoort.AFLOSSING,
        )
        val spaarPotjesRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.SPAARPOT
        )
        val zonderBetaalMethodenRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.BETAALREKENING,
            RekeningGroepSoort.RESERVERING_BUFFER,
        )
        val potjesRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.UITGAVEN,
            RekeningGroepSoort.AFLOSSING,
            RekeningGroepSoort.SPAARPOT
        )
        val balansRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.BETAALREKENING,
            RekeningGroepSoort.SPAARREKENING,
            RekeningGroepSoort.CONTANT,
            RekeningGroepSoort.CREDITCARD,
            RekeningGroepSoort.AFLOSSING,
        )
        val betaalMethodeRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.BETAALREKENING,
            RekeningGroepSoort.SPAARREKENING,
            RekeningGroepSoort.CONTANT,
            RekeningGroepSoort.CREDITCARD,
        )
        val betaalMiddelenRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.BETAALREKENING,
            RekeningGroepSoort.CONTANT,
            RekeningGroepSoort.CREDITCARD,
        )
        val vastBudgetType = arrayOf(
            BudgetType.VAST,
            BudgetType.INKOMSTEN
        )

        val betaalSoort2RekeningGroepSoort: Map<Betaling.BetalingsSoort, RekeningGroepSoort> = mapOf(
            Betaling.BetalingsSoort.INKOMSTEN to RekeningGroepSoort.INKOMSTEN,
            Betaling.BetalingsSoort.UITGAVEN to RekeningGroepSoort.UITGAVEN,
            Betaling.BetalingsSoort.BESTEDEN to RekeningGroepSoort.SPAARPOT,
            Betaling.BetalingsSoort.AFLOSSEN to RekeningGroepSoort.AFLOSSING,
            Betaling.BetalingsSoort.INCASSO_CREDITCARD to RekeningGroepSoort.CREDITCARD,
            Betaling.BetalingsSoort.SPAREN to  RekeningGroepSoort.SPAARPOT,
            Betaling.BetalingsSoort.OPNEMEN to  RekeningGroepSoort.SPAARPOT,
            Betaling.BetalingsSoort.TERUGSTORTEN to  RekeningGroepSoort.SPAARPOT,
            Betaling.BetalingsSoort.OPNEMEN_CONTANT to RekeningGroepSoort.CONTANT,
            Betaling.BetalingsSoort.STORTEN_CONTANT to RekeningGroepSoort.CONTANT,
            )
        fun RekeningGroep.isPotjeVoorNu(): Boolean {
            if (this.rekeningGroepSoort != RekeningGroepSoort.UITGAVEN && this.rekeningGroepSoort != RekeningGroepSoort.AFLOSSING ) return false
            if (this.budgetType == BudgetType.SPAREN) return false
            return true
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RekeningGroep) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun fullCopy(
        naam: String = this.naam,
        administratie: Administratie = this.administratie,
        rekeningGroepSoort: RekeningGroepSoort = this.rekeningGroepSoort,
        rekeningGroepIcoonNaam: String? = this.rekeningGroepIcoonNaam,
        sortOrder: Int = this.sortOrder,
        budgetType: BudgetType? = this.budgetType,
        rekeningen: List<Rekening> = this.rekeningen
    ) = RekeningGroep(
        this.id,
        naam,
        administratie,
        rekeningGroepSoort,
        rekeningGroepIcoonNaam,
        sortOrder,
        budgetType,
        rekeningen
    )

    enum class RekeningGroepSoort {
        BETAALREKENING, SPAARREKENING, CONTANT, CREDITCARD,
        INKOMSTEN, UITGAVEN, AFLOSSING, SPAARPOT,
        RESERVERING_BUFFER
    }

    enum class BudgetType {
        INKOMSTEN, VAST, CONTINU, SPAREN
    }

    data class RekeningGroepDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningGroepSoort: String,
        val rekeningGroepIcoonNaam: String? = null,
        val sortOrder: Int,
        val budgetType: String? = null,
        val rekeningen: List<Rekening.RekeningDTO> = emptyList()
    ) {
        fun fullCopy(
            naam: String = this.naam,
            rekeningGroepSoort: String = this.rekeningGroepSoort,
            rekeningGroepIcoonNaam: String? = this.rekeningGroepIcoonNaam,
            sortOrder: Int = this.sortOrder,
            budgetType: String? = this.budgetType,
            rekeningen: List<Rekening.RekeningDTO>
        ) = RekeningGroepDTO(
            this.id,
            naam,
            rekeningGroepSoort,
            rekeningGroepIcoonNaam,
            sortOrder,
            budgetType,
            rekeningen
        )
    }

    fun toDTO(periode: Periode? = null): RekeningGroepDTO {
        return RekeningGroepDTO(
            this.id,
            this.naam,
            this.rekeningGroepSoort.toString(),
            this.rekeningGroepIcoonNaam,
            this.sortOrder,
            this.budgetType.toString(),
            this.rekeningen.map {
                it.toDTO(periode)
            }
        )
    }

    data class RekeningGroepPerBetalingsSoort(
        val betalingsSoort: Betaling.BetalingsSoort,
        val rekeningGroepen: List<RekeningGroepDTO> = emptyList()
    )
}
