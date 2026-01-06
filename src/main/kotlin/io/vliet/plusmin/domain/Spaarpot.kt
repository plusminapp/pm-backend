package io.vliet.plusmin.domain

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "spaarpot")
class Spaarpot(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    val doelDatum: LocalDate?,
    val doelBedrag: BigDecimal?,
    @Column(columnDefinition = "TEXT")
    val notities: String? = null
) {
    fun fullCopy(
        doelDatum: LocalDate? = this.doelDatum,
        doelBedrag: BigDecimal? = this.doelBedrag,
        notities: String? = this.notities,
    ) = Spaarpot(
        this.id,
        doelDatum,
        doelBedrag,
        notities
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class SpaarpotDTO(
        val id: Long = 0,
        val doelDatum: String?,
        val doelBedrag: String?,
        val notities: String?,
    ) {
        fun fullCopy(
            doelDatum: String? = this.doelDatum,
            doelBedrag: String? = this.doelBedrag,
            notities: String? = this.notities,

            ): SpaarpotDTO = SpaarpotDTO(
            this.id,
            doelDatum,
            doelBedrag,
            notities,
        )
    }

    fun toDTO(
        saldo: BigDecimal? = null
    ): SpaarpotDTO {
        return SpaarpotDTO(
            this.id,
            this.doelDatum.toString(),
            this.doelBedrag?.toString(),
            this.notities,
        )
    }
}
