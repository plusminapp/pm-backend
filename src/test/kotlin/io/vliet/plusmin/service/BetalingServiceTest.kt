package io.vliet.plusmin

import io.vliet.plusmin.TestFixtures.testBetalingenLijst
import io.vliet.plusmin.TestFixtures.testAdministratie
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.GebruikerRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.service.BetalingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class BetalingServiceTest {

    @Mock
    lateinit var rekeningRepository: RekeningRepository

    @Mock
    lateinit var gebruikerRepository: GebruikerRepository

    @Mock
    lateinit var periodeRepository: PeriodeRepository

    @Mock
    lateinit var betalingRepository: BetalingRepository

    @InjectMocks
    lateinit var betalingService: BetalingService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

//    @Test
//    fun testCreeerBetalingLijst() {
//        `when`(rekeningRepository.findRekeningGebruikerEnNaam(testGebruiker, "Betaalrekening"))
//            .thenReturn(testBetaalrekening)
//        `when`(rekeningRepository.findRekeningGebruikerEnNaam(testGebruiker, "Uitgave"))
//            .thenReturn(testUitgave)
//        `when`(betalingRepository.save(any(Betaling::class.java))).thenReturn(testBetaling)
//        `when`(periodeRepository.getPeriodeGebruikerEnDatum(
//            anyLong(),
//            any())
//        ).thenReturn(testPeriode)
//
//        val result = betalingService.creeerBetalingLijst(testGebruiker, testBetalingenLijst)
//
//        assertEquals(1, result.size)
//        assertEquals("Test betaling", result[0].omschrijving)
//    }

    @Test
    fun testCreeerBetalingLijstThrowsExceptionWhenRekeningNotFound() {
        `when`(rekeningRepository.findRekeningAdministratieEnNaam(testAdministratie, "Rekening1")).thenReturn(null)

        assertThrows<Exception> {
            betalingService.creeerBetalingLijst(testAdministratie, testBetalingenLijst)
        }
    }
}