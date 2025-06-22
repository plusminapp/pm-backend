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
    val eindBedrag: BigDecimal,
    val dossierNummer: String,
    @Column(columnDefinition = "TEXT")
    val notities: String
) {
    fun fullCopy(
        startDatum: LocalDate = this.startDatum,
        eindBedrag: BigDecimal = this.eindBedrag,
        dossierNummer: String = this.dossierNummer,
        notities: String = this.notities,
    ) = Aflossing(
        this.id,
        startDatum,
        eindBedrag,
        dossierNummer,
        notities
    )

    data class AflossingDTO(
        val id: Long = 0,
        val startDatum: String,
        val eindBedrag: String,
        val dossierNummer: String,
        val notities: String,
    ) {
        fun fullCopy(
            startDatum: String = this.startDatum,
            eindBedrag: String = this.eindBedrag,
            dossierNummer: String = this.dossierNummer,
            notities: String = this.notities,

            ): AflossingDTO = AflossingDTO(
            this.id,
            startDatum,
            eindBedrag,
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
            this.eindBedrag.toString(),
            this.dossierNummer,
            this.notities,
        )
    }

//    data class AflossingSamenvattingDTO(
//        val aflossingNaam: String,
//        val aflossingsBedrag: BigDecimal,
//        val betaalDag: Int
//    )
//
//    fun toSamenvattingDTO(): AflossingSamenvattingDTO {
//        return AflossingSamenvattingDTO(
//            this.rekening.naam,
//            this.aflossingsBedrag,
//            this.betaalDag
//        )
//    }
}
