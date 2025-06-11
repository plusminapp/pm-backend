package io.vliet.plusmin.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "aflossing",
    uniqueConstraints = [UniqueConstraint(columnNames = ["gebruiker", "naam"])]
)
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
    val eindDatum: LocalDate,
    val eindBedrag: BigDecimal,
    val dossierNummer: String,
    @Column(columnDefinition = "TEXT")
    val notities: String
) {
    fun fullCopy(
        startDatum: LocalDate = this.startDatum,
        eindDatum: LocalDate = this.eindDatum,
        eindBedrag: BigDecimal = this.eindBedrag,
        dossierNummer: String = this.dossierNummer,
        notities: String = this.notities,
    ) = Aflossing(
        this.id,
        startDatum,
        eindDatum,
        eindBedrag,
        dossierNummer,
        notities
    )

    data class AflossingDTO(
        val id: Long = 0,
        val startDatum: String,
        val eindDatum: String,
        val eindBedrag: String,
        val dossierNummer: String,
        val notities: String,
    ) {
        fun fullCopy(
            startDatum: String = this.startDatum,
            eindDatum: String = this.eindDatum,
            eindBedrag: String = this.eindBedrag,
            dossierNummer: String = this.dossierNummer,
            notities: String = this.notities,

        ): AflossingDTO = AflossingDTO(
            this.id,
            startDatum,
            eindDatum,
            eindBedrag,
            dossierNummer,
            notities,
        )
    }

    fun toDTO(
    ): AflossingDTO {
        return AflossingDTO(
            this.id,
            this.startDatum.toString(),
            this.eindDatum.toString(),
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
