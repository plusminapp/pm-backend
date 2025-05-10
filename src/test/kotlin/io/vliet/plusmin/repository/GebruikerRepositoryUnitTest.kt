package io.vliet.plusmin.repository

import io.vliet.plusmin.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
class GebruikerRepositoryUnitTest {

    @Autowired
    lateinit var entityManager: TestEntityManager

    @Autowired
    lateinit var gebruikerRepository: GebruikerRepository

    @Test
    fun WhenFindByEmail_thenReturnGebruiker() {
        val testGebruiker = TestFixtures.testGebruiker;
        entityManager.persist(testGebruiker)
        entityManager.flush()
        val gebruikerFound = gebruikerRepository.findByEmail(email = testGebruiker.email)
        assertThat(gebruikerFound == testGebruiker)
    }
}
