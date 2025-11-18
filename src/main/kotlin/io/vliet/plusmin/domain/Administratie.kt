package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

@Entity
@Table(name = "administratie")
class Administratie(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    val naam: String = "Administratie zonder naam",
    val periodeDag: Int = 20,
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "eigenaar_id", referencedColumnName = "id")
    val eigenaar: Gebruiker,
) {

    fun fullCopy(
        naam: String = this.naam,
        periodeDag: Int = this.periodeDag,
        eigenaar: Gebruiker = this.eigenaar,
    ) = Administratie(this.id, naam, periodeDag, eigenaar)

    data class AdministratieDTO(
        val id: Long = 0,
        val naam: String = "Administratie zonder naam :-)",
        val periodeDag: Int = 20,
        val eigenaarNaam: String? = null,
        val eigenaarSubject: String? = null,
        val periodes: List<Periode.PeriodeDTO>? = emptyList(),
        val gebruikers: List<Gebruiker.GebruikerDTO>? = emptyList(),
    )

    fun toDTO(
        periodes: List<Periode> = emptyList(),
        gebruikers: List<Gebruiker> = emptyList()
    ): AdministratieDTO {
        return AdministratieDTO(
            this.id,
            this.naam,
            this.periodeDag,
            this.eigenaar.bijnaam,
            this.eigenaar.subject,
            periodes = periodes.map { it.toDTO() },
            gebruikers = gebruikers.map { it.toDTO() },
        )
    }
}
