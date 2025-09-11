package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Betaling.Betalingvalidatie
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull

@Service
class BetalingvalidatieService {
    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun valideerBetalingen(
        gebruiker: Gebruiker,
        betalingvalidatieWrapper: Betaling.BetalingValidatieWrapper
    ): Betaling.BetalingValidatieWrapper {
        val rekening = betalingvalidatieWrapper.saldoOpLaatsteBetalingDatum.let {
            rekeningRepository.findRekeningGebruikerEnNaam(
                gebruiker,
                it.rekeningNaam
            )
        } ?: throw IllegalStateException("betalingvalidatieWrapper.saldoOpLaatsteBetalingDatum.rekeningNaam is ongeldig: ${betalingvalidatieWrapper.saldoOpLaatsteBetalingDatum.rekeningNaam} voor ${gebruiker.email}.")
        val openingsBalansSaldo = saldoRepository.findLaatsteSaldoBijRekening( rekening.id).getOrNull()
            ?: throw IllegalStateException("Geen Saldo voor ${rekening.naam}/${rekening.id} voor ${gebruiker.email}.")
        val betalingen = if (openingsBalansSaldo.periode == null) {
            throw IllegalStateException("Geen Periode bij Saldo ${openingsBalansSaldo.id} voor ${gebruiker.email}.")
        } else {
            betalingRepository.findAllByGebruikerTussenDatums(
                gebruiker,
                openingsBalansSaldo.periode!!.periodeStartDatum,
                LocalDate.now()
            )
        }
        val saldoOpDatum = betalingen.fold(openingsBalansSaldo.openingsBalansSaldo) { saldo, betaling ->
            saldo + berekenMutaties(betaling, rekening)
        }
        val validatedBetalingen = betalingvalidatieWrapper.betalingen.map { betaling ->
            valideerOcrBetaling(gebruiker, betaling)
        }
        val laatsteBetalingDatum = betalingRepository.findLaatsteBetalingDatumBijRekening(gebruiker, rekening)
            ?: openingsBalansSaldo.periode!!.periodeStartDatum

        return Betaling.BetalingValidatieWrapper(
            laatsteBetalingDatum,
            Saldo.SaldoDTO(
                rekeningGroepNaam = rekening.rekeningGroep.naam,
                rekeningGroepSoort = rekening.rekeningGroep.rekeningGroepSoort,
                budgetType = rekening.rekeningGroep.budgetType,
                rekeningNaam = rekening.naam,
                sortOrder = rekening.sortOrder,
                openingsBalansSaldo = saldoOpDatum
            ),
            validatedBetalingen,
        )
    }

    fun berekenMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.bron?.id == rekening.id) -betaling.bedrag else BigDecimal.ZERO +
                if (betaling.bestemming?.id == rekening.id) betaling.bedrag else BigDecimal.ZERO
    }

    fun valideerOcrBetaling(gebruiker: Gebruiker, betaling: Betalingvalidatie): Betalingvalidatie {
        val vergelijkbareBetalingen = betalingRepository.findVergelijkbareBetalingen(
            gebruiker,
            LocalDate.parse(betaling.boekingsdatum, DateTimeFormatter.ISO_LOCAL_DATE),
            betaling.bedrag.abs(),
        )
        return betaling.fullCopy(
            bestaatAl = vergelijkbareBetalingen.isNotEmpty(),
            omschrijving = vergelijkbareBetalingen.joinToString(", ") { it.omschrijving }
        )
    }
}