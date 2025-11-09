package io.vliet.plusmin.domain

import jakarta.persistence.*

@Entity
@Table(name = "demo")
class Demo(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    @OneToOne
    @JoinColumn(name = "administratie_id")
    val administratie: Administratie,
    @OneToOne
    @JoinColumn(name = "periode_id")
    val bronPeriode: Periode
) {
    fun fullCopy(
        administratie: Administratie = this.administratie,
        bronPeriode: Periode = this.bronPeriode,
    ) = Demo(this.id, administratie, bronPeriode)
}

