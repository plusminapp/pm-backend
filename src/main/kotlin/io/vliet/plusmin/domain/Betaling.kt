package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity
@Table(
    name = "betaling",
    uniqueConstraints = [UniqueConstraint(columnNames = ["administratie_id", "sortOrder"])]
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
    @JoinColumn(name = "administratie_id", referencedColumnName = "id")
    val administratie: Administratie,
    val boekingsdatum: LocalDate,
    val reserveringsHorizon: LocalDate? = null,
    val bedrag: BigDecimal,
    @Column(columnDefinition = "TEXT")
    val omschrijving: String,
    @Enumerated(EnumType.STRING)
    val betalingsSoort: BetalingsSoort,
    val sortOrder: String,
    val isVerborgen: Boolean = false,
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
        val logger: Logger = LoggerFactory.getLogger(::javaClass.name)
        val sortableFields = setOf("id", "boekingsdatum", "status")

        val bestemmingBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.INKOMSTEN,
            BetalingsSoort.STORTEN_CONTANT,
            BetalingsSoort.OPNEMEN,
        )
        val opgenomenSaldoBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.BESTEDEN,
            BetalingsSoort.OPNEMEN,
            BetalingsSoort.TERUGSTORTEN,
        )
        val reserveringBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.P2P,
            BetalingsSoort.SP2SP,
            BetalingsSoort.P2SP,
            BetalingsSoort.SP2P,
        )
        val reserveringSpaarBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.SPAREN,
            BetalingsSoort.BESTEDEN,
        )
        val inkomstenBetalingsSoorten = listOf<BetalingsSoort>(
            BetalingsSoort.INKOMSTEN,
        )
    }

    fun fullCopy(
        administratie: Administratie = this.administratie,
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
        administratie,
        boekingsdatum,
        reserveringsHorizon,
        bedrag,
        omschrijving,
        betalingsSoort,
        sortOrder,
        isVerborgen,
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
        val isVerborgen: Boolean = false,
        val bron: String,
        val bestemming: String,
    )

    fun toDTO(): BetalingDTO {
        val (bron, bestemming) = transformeerNaarDtoBoeking(
            this.betalingsSoort,
            boeking = Pair(
                this.bron?.let { Boeking(it, this.bestemming!!) },
                this.reserveringBron?.let { Boeking(it, this.reserveringBestemming!!) })
        )
        logger.debug("Betaling.toDTO: bron: ${bron.naam}, bestemming: ${bestemming?.naam}")
        return BetalingDTO(
            this.id,
            this.boekingsdatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
            this.bedrag,
            this.omschrijving,
            this.betalingsSoort.toString(),
            this.sortOrder,
            this.isVerborgen,
            bron.naam,
            bestemming?.naam ?: "",
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
        INKOMSTEN("inkomsten"),
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
        val bestemming: Rekening?,
    )

    fun transformeerNaarDtoBoeking(
        betalingsSoort: BetalingsSoort,
        boeking: Pair<Boeking?, Boeking?>
    ): Boeking {
        return when (betalingsSoort) {
            BetalingsSoort.INKOMSTEN,
            BetalingsSoort.UITGAVEN, BetalingsSoort.BESTEDEN, BetalingsSoort.AFLOSSEN,
            BetalingsSoort.INCASSO_CREDITCARD, BetalingsSoort.OPNEMEN_CONTANT, BetalingsSoort.STORTEN_CONTANT ->
                boeking.first!!

            BetalingsSoort.TERUGSTORTEN,
            BetalingsSoort.SPAREN ->
                Boeking(boeking.first!!.bron, boeking.second!!.bestemming)

            BetalingsSoort.OPNEMEN ->
                Boeking(boeking.second!!.bron, boeking.first!!.bestemming)

            BetalingsSoort.P2P, BetalingsSoort.SP2SP,
            BetalingsSoort.P2SP, BetalingsSoort.SP2P ->
                boeking.second!!
        }
    }
}