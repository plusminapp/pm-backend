package io.vliet.plusmin.domain

import jakarta.persistence.*

@Entity
@Table(
    name = "label",
)
class Label(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val naam: String,
    @ManyToOne(fetch = FetchType.LAZY)
    val administratie: Administratie
)
