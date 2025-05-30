package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "rekening",
    uniqueConstraints = [UniqueConstraint(columnNames = ["gebruiker", "naam"])]
)
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
    @JoinColumn(name = "gebruiker_id")
    val gebruiker: Gebruiker,
    @Enumerated(EnumType.STRING)
    val rekeningSoort: RekeningSoort,
    val nummer: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val bankNaam: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val rekeningIcoonNaam: String? = null,
    val sortOrder: Int,
    @Enumerated(EnumType.STRING)
    val budgetType: BudgetType? = BudgetType.VAST,
    @OneToMany(mappedBy = "rekening", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var budgetten: List<Budget> = emptyList()
) {
    companion object {
        val sortableFields = setOf("id", "naam", "afkorting")
        val resultaatRekeningSoort = arrayOf(
            Rekening.RekeningSoort.INKOMSTEN,
            Rekening.RekeningSoort.RENTE,
            Rekening.RekeningSoort.UITGAVEN,
        )
        val balansRekeningSoort = arrayOf(
            Rekening.RekeningSoort.BETAALREKENING,
            Rekening.RekeningSoort.SPAARREKENING,
            Rekening.RekeningSoort.CONTANT,
            Rekening.RekeningSoort.CREDITCARD,
            Rekening.RekeningSoort.AFLOSSING,
            Rekening.RekeningSoort.RESERVERING
        )
    }

    fun fullCopy(
        naam: String = this.naam,
        gebruiker: Gebruiker = this.gebruiker,
        rekeningSoort: RekeningSoort = this.rekeningSoort,
        nummer: String? = this.nummer,
        bankNaam: String? = this.bankNaam,
        rekeningIcoonNaam: String? = this.rekeningIcoonNaam,
        sortOrder: Int = this.sortOrder,
        budgetType: BudgetType? = this.budgetType,
        budgetten: List<Budget> = this.budgetten
    ) = Rekening(this.id, naam, gebruiker, rekeningSoort, nummer, bankNaam, rekeningIcoonNaam, sortOrder, budgetType, budgetten)

    data class RekeningDTO(
        val id: Long = 0,
        val naam: String,
        val rekeningSoort: String,
        val nummer: String?,
        val bankNaam: String?,
        val rekeningIcoonNaam: String?,
        val saldo: BigDecimal = BigDecimal(0),
        val sortOrder: Int,
        val budgetten: List<Budget>? = emptyList()
    ){
        fun fullCopy(
            naam: String = this.naam,
            rekeningSoort: String = this.rekeningSoort,
            nummer: String? = this.nummer,
            bankNaam: String? = this.bankNaam,
            saldo: BigDecimal = this.saldo,
            sortOrder: Int = this.sortOrder,
            budgetten: List<Budget>? = this.budgetten
        ) = RekeningDTO(this.id, naam, rekeningSoort, nummer, bankNaam, rekeningIcoonNaam, saldo, sortOrder, budgetten)
    }

    fun toDTO(): RekeningDTO {
        return RekeningDTO(
            this.id,
            this.naam,
            this.rekeningSoort.toString(),
            this.nummer,
            this.bankNaam,
            this.rekeningIcoonNaam,
            sortOrder = this.sortOrder,
            budgetten = this.budgetten
        )
    }

    enum class RekeningSoort {
        BETAALREKENING, SPAARREKENING, CONTANT, CREDITCARD, AFLOSSING, RESERVERING,
        INKOMSTEN, RENTE, UITGAVEN
    }

    enum class BudgetType {
        VAST, CONTINU
    }

}
