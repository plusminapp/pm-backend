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
    val subject: String,
    val email: String? = null,
    val bijnaam: String = "Gebruiker zonder bijnaam",
    @ElementCollection(fetch = FetchType.EAGER, targetClass = Role::class)
    @Enumerated(EnumType.STRING)
    val roles: MutableSet<Role> = mutableSetOf(),
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "gebruiker_administratie",
        joinColumns = [JoinColumn(name = "gebruiker_id")],
        inverseJoinColumns = [JoinColumn(name = "administratie_id")]
    )
    var administraties: List<Administratie> = emptyList(),
) : UserDetails {

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return roles.map { SimpleGrantedAuthority(it.name) }.toMutableSet()
    }

    @JsonIgnore
    override fun getPassword(): String = ""

    @JsonIgnore
    override fun getUsername(): String = this@Gebruiker.subject

    fun fullCopy(
        subject: String = this.subject,
        email: String? = this.email,
        bijnaam: String = this.bijnaam,
        roles: MutableSet<Role> = this.roles,
        administraties: List<Administratie> = this.administraties,
    ) = Gebruiker(this.id, subject, email, bijnaam, roles, administraties)

     enum class Role {
        ROLE_ADMIN, ROLE_COORDINATOR, ROLE_VRIJWILLIGER, ROLE_HULPVRAGER
    }
    data class GebruikerDTO(
        val id: Long = 0,
        val subject: String,
        val email: String? = null,
        val bijnaam: String = "Gebruiker zonder bijnaam :-)",
        val roles: List<String> = emptyList(),
        val administraties: List<Administratie.AdministratieDTO> = emptyList(),
    )

    fun toDTO(periodes: List<Periode> = emptyList()): GebruikerDTO {
        return GebruikerDTO(
            this.id,
            this.subject,
            this.email,
            this.bijnaam,
            this.roles.map { it.toString() },
            administraties.map { it.toDTO(periodes) },
        )
    }
}
