package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Spaarpot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SpaartegoedRepository : JpaRepository<Spaarpot, Long> {}