package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Persona
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PersonaRepository : JpaRepository<Persona, Long> {

}
