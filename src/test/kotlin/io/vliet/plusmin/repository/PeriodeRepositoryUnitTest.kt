package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate


@DataJpaTest
class PeriodeRepositoryUnitTest {

    @Autowired
    lateinit var entityManager: TestEntityManager

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    final var testGebruiker = Gebruiker(bijnaam = "testUser2", email = "testUser2@example.com")

    lateinit var testPeriode: Periode;

    @BeforeEach
    fun setUp() {
        // Initialize test data before each test method
        val testGebruikerDB = entityManager.persist(this.testGebruiker)
        entityManager.flush()

        this.testPeriode = createTestPeriode(testGebruikerDB)

        entityManager.persist(testPeriode)
        entityManager.flush()
    }

    @AfterEach
    fun tearDown() {
        // Release test data after each test method
        println("teardown AfterEach, nothing to do yet")
    }

    @Test
    fun `should find 1 initial periode after @BeforeEach setup`(){
        val perioden: Iterable<*> = periodeRepository.findAll()
        assertThat(perioden.count()).isEqualTo(1)
    }

    @Test
    fun whenFindByIdOrNull_thenReturnPeriode() {
        val periodeFound = periodeRepository.findByIdOrNull(id = this.testPeriode.id)
        assertThat(periodeFound == testPeriode)
    }

    @Test
    fun whenGetPeriodeGebruikerEnDatum_thenReturnPeriode() {
        val periodeFound = periodeRepository.getPeriodeGebruikerEnDatum(
            gebruikerId = this.testGebruiker.id,
            datum = LocalDate.now()
        )
        assertThat(periodeFound == testPeriode)
    }

    fun createTestPeriode(gebruiker: Gebruiker): Periode {
        return Periode(
            periodeStartDatum = LocalDate.MAX,
            periodeEindDatum = LocalDate.MAX,
            periodeStatus = Periode.PeriodeStatus.OPEN,
            gebruiker = gebruiker
        )
    }
}
