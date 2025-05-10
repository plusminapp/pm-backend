package io.vliet.plusmin

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.vliet.plusmin.TestFixtures.testBetaalrekening
import io.vliet.plusmin.TestFixtures.testBetaling
import io.vliet.plusmin.TestFixtures.testBetalingenLijst
import io.vliet.plusmin.TestFixtures.testGebruiker
import io.vliet.plusmin.TestFixtures.testPeriode
import io.vliet.plusmin.TestFixtures.testUitgave
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.BudgetRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.service.BetalingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BetalingServiceTest {

    @MockK
    lateinit var rekeningRepository: RekeningRepository;

    @MockK
    lateinit var periodeRepository: PeriodeRepository;

    @MockK
    lateinit var betalingRepository: BetalingRepository;

    @MockK
    lateinit var budgetRepository: BudgetRepository;

    @InjectMockKs
    var betalingService = BetalingService()

    @Test
    fun testCreeerBetalingLijst() {
        every {
            rekeningRepository.findRekeningGebruikerEnNaam(
                testGebruiker,
                "Betaalrekening"
            )
        } returns (testBetaalrekening)
        every { rekeningRepository.findRekeningGebruikerEnNaam(testGebruiker, "Uitgave") } returns (testUitgave)
        every { betalingRepository.save(any()) } returns (testBetaling)
        every { betalingRepository.findMatchingBetaling(any(), any(), any(), any(), any()) } returns (listOf(
            testBetaling
        ))
        every { periodeRepository.getPeriodeGebruikerEnDatum(any(), any()) } returns (testPeriode)

        val result = betalingService.creeerBetalingLijst(testGebruiker, testBetalingenLijst)

        assertEquals(1, result.size)
        assertEquals("Test betaling", result[0].omschrijving)
    }

    @Test
    fun testCreeerBetalingLijstThrowsExceptionWhenRekeningNotFound() {
        every { rekeningRepository.findRekeningGebruikerEnNaam(testGebruiker, "Rekening1") } returns null;
        assertThrows<Exception> {
            betalingService.creeerBetalingLijst(testGebruiker, testBetalingenLijst)
        }
    }
}
