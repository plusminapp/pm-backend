package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity
@Table(
    name = "reservering",
    uniqueConstraints = [jakarta.persistence.UniqueConstraint(columnNames = ["gebruiker_id", "sortOrder"])]
)
class Reservering(
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
    @JoinColumn(name = "gebruiker_id", referencedColumnName = "id")
    val gebruiker: Gebruiker,
    val boekingsdatum: LocalDate,
    val bedrag: BigDecimal,
    @Column(columnDefinition = "TEXT")
    val omschrijving: String,
    val sortOrder: String,
    @ManyToOne
    @JoinColumn(name = "bron_id", referencedColumnName = "id")
    val bron: Rekening? = null,
    @ManyToOne
    @JoinColumn(name = "bestemming_id", referencedColumnName = "id")
    val bestemming: Rekening? = null,
) {
    fun fullCopy(
        gebruiker: Gebruiker = this.gebruiker,
        boekingsdatum: LocalDate = this.boekingsdatum,
        bedrag: BigDecimal = this.bedrag,
        omschrijving: String = this.omschrijving,
        sortOrder: String = this.sortOrder,
        bron: Rekening? = this.bron,
        bestemming: Rekening? = this.bestemming,
    ) = Reservering(
        this.id,
        gebruiker,
        boekingsdatum,
        bedrag,
        omschrijving,
        sortOrder,
        bron,
        bestemming,
    )

    data class ReserveringDTO(
        val id: Long = 0,
        val boekingsdatum: String,
        val bedrag: String,
        val omschrijving: String,
        val sortOrder: String? = null,
        val bron: String,
        val bestemming: String,
    )

    fun toDTO(): ReserveringDTO {
        return ReserveringDTO(
            this.id,
            this.boekingsdatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
            this.bedrag.toString(),
            this.omschrijving,
            this.sortOrder,
            this.bron?.naam ?: "",
            this.bestemming?.naam ?: ""
        )
    }
}