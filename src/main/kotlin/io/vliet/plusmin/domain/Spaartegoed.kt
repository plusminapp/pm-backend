package io.vliet.plusmin.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "spaartegoed")
class Spaartegoed(
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
    @Column(columnDefinition = "TEXT")
    val notities: String
) {
    fun fullCopy(
        startDatum: LocalDate = this.startDatum,
        eindBedrag: BigDecimal = this.eindBedrag,
        notities: String = this.notities,
    ) = Spaartegoed(
        this.id,
        startDatum,
        eindBedrag,
        notities
    )

    data class SpaartegoeDTO(
        val id: Long = 0,
        val startDatum: String,
        val eindBedrag: String,
        val notities: String,
    ) {
        fun fullCopy(
            startDatum: String = this.startDatum,
            eindBedrag: String = this.eindBedrag,
            notities: String = this.notities,

            ): SpaartegoeDTO = SpaartegoeDTO(
            this.id,
            startDatum,
            eindBedrag,
            notities,
        )
    }

    fun toDTO(
        saldo: BigDecimal? = null
    ): SpaartegoeDTO {
        return SpaartegoeDTO(
            this.id,
            this.startDatum.toString(),
            this.eindBedrag.toString(),
            this.notities,
        )
    }
}
