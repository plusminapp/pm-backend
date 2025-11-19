package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDate

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
    val vandaag: LocalDate? = null, // Toegevoegd voor tijdreizen
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "eigenaar_id", referencedColumnName = "id")
    val eigenaar: Gebruiker,
) {

    fun fullCopy(
        naam: String = this.naam,
        periodeDag: Int = this.periodeDag,
        vandaag: LocalDate? = this.vandaag,
        eigenaar: Gebruiker = this.eigenaar,
    ) = Administratie(this.id, naam, periodeDag, vandaag, eigenaar)

    data class AdministratieDTO(
        val id: Long = 0,
        val naam: String = "Administratie zonder naam :-)",
        val periodeDag: Int = 20,
        val vandaag: String? = null, // Toegevoegd voor tijdreizen
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
            this.vandaag?.toString(),
            this.eigenaar.bijnaam,
            this.eigenaar.subject,
            periodes = periodes.map { it.toDTO() },
            gebruikers = gebruikers.map { it.toDTO() },
        )
    }
}
