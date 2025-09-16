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
    val reserveringsHorizon: LocalDate? = null,
    val bedrag: BigDecimal,
    @Column(columnDefinition = "TEXT")
    val omschrijving: String,
    @Enumerated(EnumType.STRING)
    val betalingsSoort: BetalingsSoort,
    val sortOrder: String,
    @ManyToOne
    @JoinColumn(name = "bron_id", referencedColumnName = "id")
    val bron: Rekening? = null,
    @ManyToOne
    @JoinColumn(name = "bestemming_id", referencedColumnName = "id")
    val bestemming: Rekening? = null,
    @ManyToOne
    @JoinColumn(name = "reservering_bron_id", referencedColumnName = "id")
    val reserveringBron: Rekening? = null,
    @ManyToOne
    @JoinColumn(name = "reservering_bestemming_id", referencedColumnName = "id")
    val reserveringBestemming: Rekening? = null,
) {
    companion object {
        val sortableFields = setOf("id", "boekingsdatum", "status")

        val bestemmingBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.INKOMSTEN,
            BetalingsSoort.STORTEN_CONTANT,
            BetalingsSoort.OPNEMEN,
        )
        val reserveringBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.P2P,
            BetalingsSoort.SP2SP,
            BetalingsSoort.P2SP,
            BetalingsSoort.SP2P,
        )
        val reserveringSpaarBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.RENTE,
            BetalingsSoort.SPAREN,
            BetalingsSoort.BESTEDEN,
        )
        val inkomstenBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.INKOMSTEN,
            BetalingsSoort.RENTE,
        )
    }

    fun fullCopy(
        gebruiker: Gebruiker = this.gebruiker,
        boekingsdatum: LocalDate = this.boekingsdatum,
        reserveringsHorizon: LocalDate? = this.reserveringsHorizon,
        bedrag: BigDecimal = this.bedrag,
        omschrijving: String = this.omschrijving,
        betalingsSoort: BetalingsSoort = this.betalingsSoort,
        sortOrder: String = this.sortOrder,
        bron: Rekening? = this.bron,
        bestemming: Rekening? = this.bestemming,
        reserveringBron: Rekening? = this.reserveringBron,
        reserveringBestemming: Rekening? = this.reserveringBestemming,
    ) = Betaling(
        this.id,
        gebruiker,
        boekingsdatum,
        reserveringsHorizon,
        bedrag,
        omschrijving,
        betalingsSoort,
        sortOrder,
        bron,
        bestemming,
        reserveringBron,
        reserveringBestemming
    )

    data class BetalingDTO(
        val id: Long = 0,
        val boekingsdatum: String,
        val bedrag: BigDecimal,
        val omschrijving: String,
        val betalingsSoort: String,
        val sortOrder: String? = null,
        val bron: String,
        val bestemming: String,
        val reserveringBron: String? = null,
        val reserveringBestemming: String? = null,
    )

    fun toDTO(): BetalingDTO {
        return BetalingDTO(
            this.id,
            this.boekingsdatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
            this.bedrag,
            this.omschrijving,
            this.betalingsSoort.toString(),
            this.sortOrder,
            this.bron?.naam ?: "",
            this.bestemming?.naam ?: "",
            this.reserveringBron?.naam ?: "",
            this.reserveringBestemming?.naam ?: "",
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
        BESTEDEN("besteden"),
        AFLOSSEN("aflossen"),
        SPAREN("sparen"),
        OPNEMEN("opnemen"),
        TERUGSTORTEN("terugstorten"),
        INCASSO_CREDITCARD("incasso_creditcard"),
        OPNEMEN_CONTANT("opnemen_contant"),
        STORTEN_CONTANT("storten_contant"),
        P2P("potje2potje"),
        SP2SP("spaarpotje2spaarpotje"),
        P2SP("potje2spaarpotje"),
        SP2P("spaarpotje2potje")
    }

    data class Boeking(
        val bron: Rekening,
        val bestemming: Rekening,
    )
}