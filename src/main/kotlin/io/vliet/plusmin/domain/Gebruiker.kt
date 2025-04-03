package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

@Entity
@Table(name = "gebruiker")
class Gebruiker(
    @Id
    @GeneratedValue(generator = "hibernate_sequence", strategy = GenerationType.SEQUENCE)
    @SequenceGenerator(
        name = "hibernate_sequence",
        sequenceName = "hibernate_sequence",
        allocationSize = 1
    )
    val id: Long = 0,
    @Column(unique = true)
    val email: String,
    val bijnaam: String = "Gebruiker zonder bijnaam",
    val periodeDag: Int = 20,
    @ElementCollection(fetch = FetchType.EAGER, targetClass = Role::class)
    @Enumerated(EnumType.STRING)
    val roles: MutableSet<Role> = mutableSetOf(),
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "vrijwilliger_id", referencedColumnName = "id")
    val vrijwilliger: Gebruiker? = null,
    @OneToMany(mappedBy = "gebruiker", fetch = FetchType.EAGER)
    var rekeningen: List<Rekening> = emptyList()
) : UserDetails {

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return roles.map { SimpleGrantedAuthority(it.name) }.toMutableSet()
    }

    @JsonIgnore
    override fun getPassword(): String = ""

    @JsonIgnore
    override fun getUsername(): String = email

    fun with(rekening: List<Rekening>): Gebruiker {
        this.rekeningen = rekening
        return this
    }

    fun fullCopy(
        email: String = this.email,
        bijnaam: String = this.bijnaam,
        periodeDag: Int = this.periodeDag,
        roles: MutableSet<Role> = this.roles,
        vrijwilliger: Gebruiker? = this.vrijwilliger,
        rekeningen: List<Rekening> = this.rekeningen,
    ) = Gebruiker(this.id, email, bijnaam, periodeDag, roles, vrijwilliger, rekeningen)

     enum class Role {
        ROLE_ADMIN, ROLE_COORDINATOR, ROLE_VRIJWILLIGER, ROLE_HULPVRAGER
    }
    data class GebruikerDTO(
        val id: Long = 0,
        val email: String,
        val bijnaam: String = "Gebruiker zonder bijnaam :-)",
        val periodeDag: Int = 20,
        val roles: List<String> = emptyList(),
        val vrijwilligerEmail: String = "",
        val vrijwilligerBijnaam: String = "",
        val rekeningen: List<Rekening>? = emptyList(),
        val periodes: List<Periode.PeriodeDTO>? = emptyList(),
        val aflossingen: List<Aflossing.AflossingSamenvattingDTO>? = emptyList(),
    )

    data class GebruikerMetHulpvragersDTO(
        val gebruiker: GebruikerDTO,
        val hulpvragers: List<GebruikerDTO>
    )

}
