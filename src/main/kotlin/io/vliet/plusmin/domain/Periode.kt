package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
    De Saldi tabel bevat de saldi van de rekeningen van een gebruiker (hulpvrager) op 1 moment in de tijd:
    laatste dag van de Periode worden
 */

@Entity
@Table(
    name = "periode",
    uniqueConstraints = [UniqueConstraint(columnNames = ["gebruiker_id", "periodeStartDatum"])]
)
class Periode(
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
    @JoinColumn(name = "gebruiker_id")
    val gebruiker: Gebruiker,
    val periodeStartDatum: LocalDate,
    val periodeEindDatum: LocalDate,
    @Enumerated(EnumType.STRING)
    val periodeStatus: PeriodeStatus = PeriodeStatus.HUIDIG,
) {
    fun fullCopy(
        gebruiker: Gebruiker = this.gebruiker,
        periodeStartDatum: LocalDate = this.periodeStartDatum,
        periodeEindDatum: LocalDate = this.periodeEindDatum,
        periodeStatus: PeriodeStatus = this.periodeStatus,
    ) = Periode(this.id, gebruiker, periodeStartDatum, periodeEindDatum, periodeStatus)

    data class PeriodeDTO(
        val id: Long = 0,
        val periodeStartDatum: String,
        val periodeEindDatum: String? = LocalDate.parse(periodeStartDatum).plusMonths(1).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
        val periodeStatus: PeriodeStatus? = PeriodeStatus.OPGERUIMD,
        var saldoLijst: List<Saldo.SaldoDTO>? = emptyList()
    )

    fun toDTO(): PeriodeDTO {
        return PeriodeDTO(
            this.id,
            this.periodeStartDatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
            this.periodeEindDatum.format(DateTimeFormatter.ISO_LOCAL_DATE),
            this.periodeStatus,
        )
    }

    companion object {
        val openPeriodes = listOf(PeriodeStatus.HUIDIG, PeriodeStatus.OPEN)
        val geslotenPeriodes = listOf(PeriodeStatus.GESLOTEN, PeriodeStatus.OPGERUIMD)
    }

    enum class PeriodeStatus {
        HUIDIG, OPEN, GESLOTEN, OPGERUIMD
    }
}