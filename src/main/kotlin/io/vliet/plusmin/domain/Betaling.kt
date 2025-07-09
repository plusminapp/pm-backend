package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity
@Table(
    name = "betaling",
    uniqueConstraints = [jakarta.persistence.UniqueConstraint(columnNames = ["gebruiker_id", "sortOrder"])]
)
class Betaling(
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
    @Enumerated(EnumType.STRING)
    val betalingsSoort: BetalingsSoort,
    val sortOrder: String,
    @ManyToOne
    @JoinColumn(name = "bron_id", referencedColumnName = "id")
    val bron: Rekening,
    @ManyToOne
    @JoinColumn(name = "bestemming_id", referencedColumnName = "id")
    val bestemming: Rekening,
) {
    companion object {
        val sortableFields = setOf("id", "boekingsdatum", "status")

        val bestemmingBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.INKOMSTEN,
            BetalingsSoort.STORTEN_CONTANT,
            BetalingsSoort.OPNEMEN_SPAARREKENING,
        )
    }

    fun fullCopy(
        gebruiker: Gebruiker = this.gebruiker,
        boekingsdatum: LocalDate = this.boekingsdatum,
        bedrag: BigDecimal = this.bedrag,
        omschrijving: String = this.omschrijving,
        betalingsSoort: BetalingsSoort = this.betalingsSoort,
        sortOrder: String = this.sortOrder,
        bron: Rekening = this.bron,
        bestemming: Rekening = this.bestemming,
    ) = Betaling(
        this.id,
        gebruiker,
        boekingsdatum,
        bedrag,
        omschrijving,
        betalingsSoort,
        sortOrder,
        bron,
        bestemming,
    )

    data class BetalingDTO(
        val id: Long = 0,
        val boekingsdatum: String,
        val bedrag: String,
        val omschrijving: String,
        val betalingsSoort: String,
        val sortOrder: String? = null,
        val bron: String,
        val bestemming: String,
    )

    fun toDTO(): BetalingDTO {
        return BetalingDTO(
            this.id,
            this.boekingsdatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
            this.bedrag.toString(),
            this.omschrijving,
            this.betalingsSoort.toString(),
            this.sortOrder,
            this.bron.naam,
            this.bestemming.naam
        )
    }

    data class Betalingvalidatie(
        val boekingsdatum: String,
        val bedrag: BigDecimal,
        val omschrijving: String?,
        val ocrOmschrijving: String?,
        val sortOrder: String?,
        val bestaatAl: Boolean? = false,
    ) {
        fun fullCopy(
            boekingsdatum: String = this.boekingsdatum,
            bedrag: BigDecimal = this.bedrag,
            omschrijving: String? = this.ocrOmschrijving,
            ocrOmschrijving: String? = this.ocrOmschrijving,
            sortOrder: String? = this.sortOrder,
            bestaatAl: Boolean? = this.bestaatAl
        ) = Betalingvalidatie(boekingsdatum, bedrag, omschrijving, ocrOmschrijving, sortOrder, bestaatAl)
    }

    data class BetalingValidatieWrapper(
        val laatsteBetalingDatum: LocalDate? = null,
        val saldoOpLaatsteBetalingDatum: Saldo.SaldoDTO,
        val betalingen: List<Betalingvalidatie> = emptyList(),
    )

    enum class BetalingsSoort(
        val omschrijving: String
    ) {
        INKOMSTEN("Inkomsten"),
        RENTE("Rente"),
        UITGAVEN("Uitgaven"),
        LENEN("lenen"),
        AFLOSSEN("aflossen"),
//        TOEVOEGEN_SPAARTEGOED("toevoegen_spaartegoed"),
        BESTEDEN_SPAARTEGOED("besteden_spaartegoed"),
        INCASSO_CREDITCARD("incasso_creditcard"),
        OPNEMEN_SPAARREKENING("opnemen_spaarrekening"),
        STORTEN_SPAARREKENING("storten_spaarrekening"),
        OPNEMEN_CONTANT("opnemen_contant"),
        STORTEN_CONTANT("storten_contant")
    }
}