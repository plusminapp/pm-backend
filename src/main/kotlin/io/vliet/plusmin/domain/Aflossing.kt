package io.vliet.plusmin.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "aflossing")
class Aflossing(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    val startDatum: LocalDate,
    val schuldOpStartDatum: BigDecimal,
    val dossierNummer: String,
    @Column(columnDefinition = "TEXT")
    val notities: String
) {
    fun fullCopy(
        startDatum: LocalDate = this.startDatum,
        schuldOpStartDatum: BigDecimal = this.schuldOpStartDatum,
        dossierNummer: String = this.dossierNummer,
        notities: String = this.notities,
    ) = Aflossing(
        this.id,
        startDatum,
        schuldOpStartDatum,
        dossierNummer,
        notities
    )

    data class AflossingDTO(
        val id: Long = 0,
        val startDatum: String,
        val schuldOpStartDatum: String,
        val dossierNummer: String,
        val notities: String,
    ) {
        fun fullCopy(
            startDatum: String = this.startDatum,
            schuldOpStartDatum: String = this.schuldOpStartDatum,
            dossierNummer: String = this.dossierNummer,
            notities: String = this.notities,

            ): AflossingDTO = AflossingDTO(
            this.id,
            startDatum,
            schuldOpStartDatum,
            dossierNummer,
            notities,
        )
    }

    fun toDTO(
        saldo: BigDecimal? = null
    ): AflossingDTO {
        return AflossingDTO(
            this.id,
            this.startDatum.toString(),
            this.schuldOpStartDatum.toString(),
            this.dossierNummer,
            this.notities,
        )
    }
}
