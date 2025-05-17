package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.*

@Entity
@Table(
    name = "rekening_groep",
    uniqueConstraints = [UniqueConstraint(columnNames = ["gebruiker", "naam"])]
)
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
    @JoinColumn(name = "gebruiker_id")
    val gebruiker: Gebruiker,
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
            RekeningGroepSoort.RENTE,
            RekeningGroepSoort.UITGAVEN,
        )
        val balansRekeningGroepSoort = arrayOf(
            RekeningGroepSoort.BETAALREKENING,
            RekeningGroepSoort.SPAARREKENING,
            RekeningGroepSoort.CONTANT,
            RekeningGroepSoort.CREDITCARD,
            RekeningGroepSoort.AFLOSSING,
            RekeningGroepSoort.RESERVERING
        )
    }

    fun fullCopy(
        naam: String = this.naam,
        gebruiker: Gebruiker = this.gebruiker,
        rekeningGroepSoort: RekeningGroepSoort = this.rekeningGroepSoort,
        rekeningGroepIcoonNaam: String? = this.rekeningGroepIcoonNaam,
        sortOrder: Int = this.sortOrder,
        budgetType: BudgetType? = this.budgetType,
        rekeningen: List<Rekening> = this.rekeningen
    ) = RekeningGroep(
        this.id,
        naam,
        gebruiker,
        rekeningGroepSoort,
        rekeningGroepIcoonNaam,
        sortOrder,
        budgetType,
        rekeningen
    )

    enum class RekeningGroepSoort {
        BETAALREKENING, SPAARREKENING, CONTANT, CREDITCARD, AFLOSSING, RESERVERING,
        INKOMSTEN, RENTE, UITGAVEN
    }

    enum class BudgetType {
        VAST, CONTINU
    }

    data class RekeningGroepDTO(
        val id: Long = 0,
        val naam: String,
        val gebruiker: Gebruiker,
        val rekeningGroepSoort: String,
        val rekeningGroepIcoonNaam: String? = null,
        val sortOrder: Int,
        val budgetType: String,
    ) {
        fun fullCopy(
            naam: String = this.naam,
            gebruiker: Gebruiker = this.gebruiker,
            rekeningGroepSoort: String = this.rekeningGroepSoort,
            rekeningGroepIcoonNaam: String? = this.rekeningGroepIcoonNaam,
            sortOrder: Int = this.sortOrder,
            budgetType: String = this.budgetType
        ) = RekeningGroepDTO(
            this.id,
            naam,
            gebruiker,
            rekeningGroepSoort,
            rekeningGroepIcoonNaam,
            sortOrder,
            budgetType
        )

        fun fromDTO(
            naam: String = this.naam,
            gebruiker: Gebruiker = this.gebruiker,
            rekeningGroepSoort: String = this.rekeningGroepSoort,
            rekeningGroepIcoonNaam: String? = this.rekeningGroepIcoonNaam,
            sortOrder: Int = this.sortOrder,
            budgetType: String = this.budgetType
        ) = RekeningGroep(
            this.id,
            naam,
            gebruiker,
            enumValueOf<RekeningGroepSoort>(rekeningGroepSoort),
            rekeningGroepIcoonNaam,
            sortOrder,
            enumValueOf<BudgetType>(budgetType)
        )
    }

    fun toDTO(): RekeningGroepDTO {
        return RekeningGroepDTO(
            this.id,
            this.naam,
            this.gebruiker,
            this.rekeningGroepSoort.toString(),
            this.rekeningGroepIcoonNaam,
            this.sortOrder,
            this.budgetType.toString()
        )
    }
}
