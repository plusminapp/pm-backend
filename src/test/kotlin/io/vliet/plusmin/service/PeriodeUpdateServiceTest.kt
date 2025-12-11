package io.vliet.plusmin.service

import io.vliet.plusmin.TestFixtures.testAdministratie
import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PeriodeUpdateServiceTest {

    @Mock
    lateinit var periodeRepository: PeriodeRepository

    @Mock
    lateinit var periodeService: PeriodeService

    @Mock
    lateinit var saldoRepository: SaldoRepository

    @Mock
    lateinit var standInPeriodeService: StandInPeriodeService

    @Mock
    lateinit var reserveringService: ReserveringService

    @Mock
    lateinit var updateSpaarSaldiService: UpdateSpaarSaldiService

    @Mock
    lateinit var rekeningRepository: RekeningRepository

    @Mock
    lateinit var betalingRepository: BetalingRepository

    @InjectMocks
    lateinit var periodeUpdateService: PeriodeUpdateService

    private lateinit var testRekeningGroep: RekeningGroep
    private lateinit var testRekening: Rekening
    private lateinit var vorigePeriode: Periode
    private lateinit var huidigePeriode: Periode
    private lateinit var testSaldo: Saldo
    private lateinit var testSaldoDTO: Saldo.SaldoDTO

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        testRekeningGroep = RekeningGroep(
            administratie = testAdministratie,
            naam = "Test Groep",
            rekeningGroepSoort = RekeningGroep.RekeningGroepSoort.UITGAVEN,
            sortOrder = 1,
            budgetType = RekeningGroep.BudgetType.VAST
        )

        testRekening = Rekening(
            id = 1L,
            naam = "Test Rekening",
            budgetBetaalDag = 20,
            rekeningGroep = testRekeningGroep,
            budgetBedrag = BigDecimal("100.00"),
            budgetVariabiliteit = 10,
            sortOrder = 1
        )

        vorigePeriode = Periode(
            id = 1L,
            administratie = testAdministratie,
            periodeStartDatum = LocalDate.of(2023, 1, 1),
            periodeEindDatum = LocalDate.of(2023, 1, 31),
            periodeStatus = Periode.PeriodeStatus.GESLOTEN
        )

        huidigePeriode = Periode(
            id = 2L,
            administratie = testAdministratie,
            periodeStartDatum = LocalDate.of(2023, 2, 1),
            periodeEindDatum = LocalDate.of(2023, 2, 28),
            periodeStatus = Periode.PeriodeStatus.OPEN
        )

        testSaldo = Saldo(
            id = 1L,
            rekening = testRekening,
            openingsBalansSaldo = BigDecimal("1000.00"),
            openingsReserveSaldo = BigDecimal.ZERO,
            openingsOpgenomenSaldo = BigDecimal.ZERO,
            openingsAchterstand = BigDecimal.ZERO,
            budgetMaandBedrag = BigDecimal("100.00"),
            periodeBetaling = BigDecimal("50.00"),
            periodeReservering = BigDecimal.ZERO,
            periodeOpgenomenSaldo = BigDecimal.ZERO,
            correctieBoeking = BigDecimal.ZERO,
            periode = vorigePeriode
        )

        testSaldoDTO = Saldo.SaldoDTO(
            rekeningNaam = "Test Rekening",
            openingsBalansSaldo = BigDecimal("1000.00"),
            openingsAchterstand = BigDecimal.ZERO,
            budgetMaandBedrag = BigDecimal("100.00"),
            periodeBetaling = BigDecimal("50.00")
        )
    }

    @Test
    fun `checkPeriodeSluiten - periode niet gevonden - gooit exceptie`() {
        // Arrange
        val periodeLijst = listOf(vorigePeriode, huidigePeriode)

        `when`(periodeRepository.getPeriodesVoorAdministrtatie(testAdministratie)).thenReturn(periodeLijst)

        // Act & Assert
        assertThrows<PM_PeriodeNotFoundException> {
            periodeUpdateService.checkPeriodeSluiten(testAdministratie, 999L)
        }
    }

    @Test
    fun `ruimPeriodeOp - succesvol`() {
        // Arrange
        val geslotenPeriode = huidigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.GESLOTEN)
        val periodeLijst = listOf(vorigePeriode, geslotenPeriode)

        `when`(periodeRepository.getPeriodesVoorAdministrtatie(testAdministratie)).thenReturn(periodeLijst)
        `when`(periodeRepository.save(org.mockito.ArgumentMatchers.any<Periode>())).thenAnswer { it.arguments[0] }

        // Act
        periodeUpdateService.ruimPeriodeOp(testAdministratie, geslotenPeriode)

        // Assert
        verify(betalingRepository, times(1)).deleteAllByAdministratieTotEnMetDatum(testAdministratie, geslotenPeriode.periodeEindDatum)
        verify(periodeRepository, times(2)).save(org.mockito.ArgumentMatchers.any<Periode>()) // Voor beide periodes
    }

    @Test
    fun `ruimPeriodeOp - periode niet gesloten - gooit exceptie`() {
        // Arrange
        val openPeriode = huidigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.OPEN)

        // Act & Assert
        assertThrows<PM_PeriodeNietGeslotenException> {
            periodeUpdateService.ruimPeriodeOp(testAdministratie, openPeriode)
        }
    }

    @Test
    fun `heropenPeriode - succesvol`() {
        // Arrange
        val geslotenPeriode = huidigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.GESLOTEN)

        `when`(periodeService.getLaatstGeslotenOfOpgeruimdePeriode(testAdministratie)).thenReturn(geslotenPeriode)
        `when`(periodeRepository.save(org.mockito.ArgumentMatchers.any<Periode>())).thenAnswer { it.arguments[0] }

        // Act
        periodeUpdateService.heropenPeriode(testAdministratie, geslotenPeriode)

        // Assert
        verify(saldoRepository, times(1)).deleteByPeriode(geslotenPeriode)
        verify(periodeRepository, times(1)).save(org.mockito.ArgumentMatchers.any<Periode>())
    }

    @Test
    fun `heropenPeriode - periode niet gesloten - gooit exceptie`() {
        // Arrange
        val openPeriode = huidigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.OPEN)

        // Act & Assert
        assertThrows<PM_PeriodeNietGeslotenException> {
            periodeUpdateService.heropenPeriode(testAdministratie, openPeriode)
        }
    }

    @Test
    fun `heropenPeriode - niet de laatst gesloten periode - gooit exceptie`() {
        // Arrange
        val geslotenPeriode = huidigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.GESLOTEN)
        val anderePeriode = Periode(
            id = 3L,
            administratie = testAdministratie,
            periodeStartDatum = LocalDate.of(2023, 3, 1),
            periodeEindDatum = LocalDate.of(2023, 3, 31),
            periodeStatus = Periode.PeriodeStatus.GESLOTEN
        )

        `when`(periodeService.getLaatstGeslotenOfOpgeruimdePeriode(testAdministratie)).thenReturn(anderePeriode)

        // Act & Assert
        assertThrows<PM_PeriodeNietLaatstGeslotenException> {
            periodeUpdateService.heropenPeriode(testAdministratie, geslotenPeriode)
        }
    }

    @Test
    fun `wijzigPeriodeOpening - succesvol`() {
        // Arrange
        val periodeLijst = listOf(vorigePeriode, huidigePeriode)
        val bestaandeSaldi = listOf(testSaldo)
        val nieuweOpeningsSaldi = listOf(
            Saldo.SaldoDTO(
                rekeningNaam = "Test Rekening",
                openingsBalansSaldo = BigDecimal("1200.00"),
                openingsAchterstand = BigDecimal.ZERO,
                budgetMaandBedrag = BigDecimal("100.00"),
                periodeBetaling = BigDecimal.ZERO
            )
        )

        `when`(periodeRepository.getPeriodesVoorAdministrtatie(testAdministratie)).thenReturn(periodeLijst)
        doNothing().`when`(updateSpaarSaldiService).updateSpaarpotSaldo(testAdministratie)
        doNothing().`when`(reserveringService).updateOpeningsReserveringsSaldo(testAdministratie)
        `when`(saldoRepository.findAllByPeriode(vorigePeriode)).thenReturn(bestaandeSaldi)
        `when`(saldoRepository.save(org.mockito.ArgumentMatchers.any<Saldo>())).thenAnswer {
            it.arguments[0] as Saldo
        }

        // Act
        val result = periodeUpdateService.wijzigPeriodeOpening(testAdministratie, 2L, nieuweOpeningsSaldi)

        // Assert
        assertNotNull(result)
        assertEquals(1, result.size)
        verify(saldoRepository, times(1)).save(org.mockito.ArgumentMatchers.any<Saldo>())
    }

    @Test
    fun `wijzigPeriodeOpening - rekening niet gevonden - gooit exceptie`() {
        // Arrange
        val periodeLijst = listOf(vorigePeriode, huidigePeriode)
        val bestaandeSaldi = listOf(testSaldo)
        val nieuweOpeningsSaldi = listOf(
            Saldo.SaldoDTO(
                rekeningNaam = "Onbekende Rekening",
                openingsBalansSaldo = BigDecimal("1200.00"),
                openingsAchterstand = BigDecimal.ZERO,
                budgetMaandBedrag = BigDecimal("100.00"),
                periodeBetaling = BigDecimal.ZERO
            )
        )

        `when`(periodeRepository.getPeriodesVoorAdministrtatie(testAdministratie)).thenReturn(periodeLijst)
        `when`(saldoRepository.findAllByPeriode(vorigePeriode)).thenReturn(bestaandeSaldi)

        // Act & Assert
        assertThrows<PM_GeenSaldoVoorRekeningException> {
            periodeUpdateService.wijzigPeriodeOpening(testAdministratie, 2L, nieuweOpeningsSaldi)
        }
    }
}