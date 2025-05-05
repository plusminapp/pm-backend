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
    @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "rekening_id", nullable = false)
    val rekening: Rekening,
    val startDatum: LocalDate,
    val eindDatum: LocalDate,
    val eindBedrag: BigDecimal,
    val aflossingsBedrag: BigDecimal,
    val betaalDag: Int,
    val dossierNummer: String,
    @Column(columnDefinition = "TEXT")
    val notities: String
) {
    fun fullCopy(
        rekening: Rekening = this.rekening,
        startDatum: LocalDate = this.startDatum,
        eindDatum: LocalDate = this.eindDatum,
        eindBedrag: BigDecimal = this.eindBedrag,
        aflossingsBedrag: BigDecimal = this.aflossingsBedrag,
        betaalDag: Int = this.betaalDag,
        dossierNummer: String = this.dossierNummer,
        notities: String = this.notities,
    ) = Aflossing(
        this.id,
        rekening,
        startDatum,
        eindDatum,
        eindBedrag,
        aflossingsBedrag,
        betaalDag,
        dossierNummer,
        notities
    )

    data class AflossingDTO(
        val id: Long = 0,
        val rekening: Rekening.RekeningDTO,
        val startDatum: String,
        val eindDatum: String,
        val eindBedrag: String,
        val aflossingsBedrag: String,
        val betaalDag: Int,
        val dossierNummer: String,
        val notities: String,
        val aflossingPeilDatum: String? = null,
        val aflossingOpPeilDatum: BigDecimal? = null,
        val aflossingBetaling: BigDecimal? = null,
        val deltaStartPeriode: BigDecimal? = null,
        val saldoStartPeriode: BigDecimal? = null,
        val aflossingMoetBetaaldZijn: Boolean? = null,
        val actueleStand: BigDecimal? = null,
        val actueleAchterstand: BigDecimal? = null,
        val betaaldBinnenAflossing: BigDecimal? = null,
        val meerDanVerwacht: BigDecimal? = null,
        val minderDanVerwacht: BigDecimal? = null,
        val meerDanMaandAflossing: BigDecimal? = null,
    ) {
        fun fullCopy(
            rekening: Rekening.RekeningDTO = this.rekening,
            startDatum: String = this.startDatum,
            eindDatum: String = this.eindDatum,
            eindBedrag: String = this.eindBedrag,
            aflossingsBedrag: String = this.aflossingsBedrag,
            betaalDag: Int = this.betaalDag,
            dossierNummer: String = this.dossierNummer,
            notities: String = this.notities,
            aflossingPeilDatum: String? = this.aflossingPeilDatum,
            aflossingOpPeilDatum: BigDecimal? = this.aflossingOpPeilDatum,
            aflossingBetaling: BigDecimal? = this.aflossingBetaling,
            deltaStartPeriode: BigDecimal? = this.deltaStartPeriode,
            saldoStartPeriode: BigDecimal? = this.saldoStartPeriode,
            aflossingMoetBetaaldZijn: Boolean? = this.aflossingMoetBetaaldZijn,
            actueleStand: BigDecimal? = this.actueleStand,
            actueleAchterstand: BigDecimal? = this.actueleAchterstand,
            betaaldBinnenAflossing: BigDecimal? = this.betaaldBinnenAflossing,
            meerDanVerwacht: BigDecimal? = this.meerDanVerwacht,
            minderDanVerwacht: BigDecimal? = this.minderDanVerwacht,
            meerDanMaandAflossing: BigDecimal? = this.meerDanMaandAflossing,
        ): AflossingDTO = AflossingDTO(
            this.id,
            rekening,
            startDatum,
            eindDatum,
            eindBedrag,
            aflossingsBedrag,
            betaalDag,
            dossierNummer,
            notities,
            aflossingPeilDatum,
            aflossingOpPeilDatum,
            aflossingBetaling,
            deltaStartPeriode,
            saldoStartPeriode,
            aflossingMoetBetaaldZijn,
            actueleStand,
            actueleAchterstand,
            betaaldBinnenAflossing,
            meerDanVerwacht,
            minderDanVerwacht,
            meerDanMaandAflossing,
        )
    }

    fun toDTO(
        aflossingPeilDatum: String? = null,
        aflossingOpPeilDatum: BigDecimal? = null,
        aflossingBetaling: BigDecimal? = null,
        deltaStartPeriode: BigDecimal? = null,
        saldoStartPeriode: BigDecimal? = null,
        aflossingMoetBetaaldZijn: Boolean,
        actueleStand: BigDecimal? = null,
        actueleAchterstand: BigDecimal? = null,
        betaaldBinnenAflossing: BigDecimal? = null,
        meerDanVerwacht: BigDecimal? = null,
        minderDanVerwacht: BigDecimal? = null,
        meerDanMaandAflossing: BigDecimal? = null,
    ): AflossingDTO {
        return AflossingDTO(
            this.id,
            this.rekening.toDTO(),
            this.startDatum.toString(),
            this.eindDatum.toString(),
            this.eindBedrag.toString(),
            this.aflossingsBedrag.toString(),
            this.betaalDag,
            this.dossierNummer,
            this.notities,
            aflossingPeilDatum = aflossingPeilDatum,
            aflossingOpPeilDatum = aflossingOpPeilDatum,
            aflossingBetaling = aflossingBetaling,
            deltaStartPeriode = deltaStartPeriode,
            saldoStartPeriode = saldoStartPeriode,
            aflossingMoetBetaaldZijn = aflossingMoetBetaaldZijn,
            actueleStand = actueleStand,
            actueleAchterstand = actueleAchterstand,
            betaaldBinnenAflossing = betaaldBinnenAflossing,
            meerDanVerwacht = meerDanVerwacht,
            minderDanVerwacht = minderDanVerwacht,
            meerDanMaandAflossing = meerDanMaandAflossing,
        )
    }

    data class AflossingSamenvattingDTO(
        val aflossingNaam: String,
        val aflossingsBedrag: BigDecimal,
        val betaalDag: Int
    )

    fun toSamenvattingDTO(): AflossingSamenvattingDTO {
        return AflossingSamenvattingDTO(
            this.rekening.naam,
            this.aflossingsBedrag,
            this.betaalDag
        )
    }
}
