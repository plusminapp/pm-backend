package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Aflossing
import io.vliet.plusmin.domain.Spaartegoed
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SpaartegoedRepository : JpaRepository<Spaartegoed, Long> {}