package io.vliet.plusmin

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Periode
import java.math.BigDecimal
import java.time.LocalDate

object TestFixtures {

    val testGebruiker = Gebruiker(
        subject = "testgebruiker",
        bijnaam = "testgebruiker",
    )

    val testAdministratie = Administratie(
        naam = "testUser1",
        periodeDag = 20,
        eigenaar = testGebruiker
    )

//    val testBetaalrekening = Rekening(
//        gebruiker = testGebruiker,
//        naam = "Betaalrekening",
//        rekeningSoort = RekeningGroep.RekeningGroepSoort.BETAALREKENING,
//        sortOrder = 0
//    )
//
//    val testUitgave = Rekening(
//        gebruiker = testGebruiker,
//        naam = "Uitgave",
//        rekeningSoort = RekeningGroep.RekeningGroepSoort.VASTE_UITGAVEN,
//        sortOrder = 10
//    )

    val testBetalingDTO = Betaling.BetalingDTO(
        boekingsdatum = "2023-01-01",
        bedrag = BigDecimal(100.00),
        omschrijving = "Test betaling",
        betalingsSoort = "UITGAVEN",
        bron = "Betaalrekening",
        bestemming = "Uitgave",
        sortOrder = "20230101.900",
    )

    val testBetalingenLijst = listOf(testBetalingDTO)

//    val testBetaling = Betaling(
//        id = 1,
//        gebruiker = testGebruiker,
//        boekingsdatum = LocalDate.parse("2023-01-01"),
//        bedrag = BigDecimal(100.00),
//        omschrijving = "Test betaling",
//        betalingsSoort = Betaling.BetalingsSoort.UITGAVEN,
//        bron = testBetaalrekening,
//        sortOrder = "20230101.900",
//        bestemming = testUitgave
//    )

    val testPeriode = Periode(
        id = 1,
        administratie = testAdministratie,
        periodeStartDatum = LocalDate.MAX,
        periodeEindDatum = LocalDate.MAX,
        periodeStatus = Periode.PeriodeStatus.OPEN
    )
}