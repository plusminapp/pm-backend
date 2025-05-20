package io.vliet.plusmin.service

import io.vliet.plusmin.controller.SaldoController
import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.RekeningGroep.Companion.balansRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.resultaatRekeningGroepSoort
import io.vliet.plusmin.repository.BetalingRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
class SaldoService {
    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    @Autowired
    lateinit var budgetService: BudgetService

    @Autowired
    lateinit var aflossingService: AflossingService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getStandOpDatum(
        openingPeriode: Periode,
        periode: Periode,
        peilDatum: LocalDate
    ): SaldoController.StandDTO {
        logger.warn("openingPeriode: ${openingPeriode.periodeStartDatum}, periodeStartDatum: ${periode}, peilDatum: ${peilDatum}")
        val openingsSaldi = getOpeningSaldi(openingPeriode)
        val mutatiePeriodeOpeningLijst =
            berekenMutatieLijstOpDatum(
                openingPeriode.gebruiker,
                openingPeriode.periodeStartDatum,
                periode.periodeStartDatum.minusDays(1)
            )
        val balansSaldiBijOpening = berekenSaldiOpDatum(openingsSaldi, mutatiePeriodeOpeningLijst)
        val mutatiePeilDatumLijst =
            berekenMutatieLijstOpDatum(openingPeriode.gebruiker, periode.periodeStartDatum, peilDatum)
        val balansSaldiOpDatum = berekenSaldiOpDatum(balansSaldiBijOpening, mutatiePeilDatumLijst)

        val openingsBalans =
            balansSaldiBijOpening
                .filter { it.rekening.rekeningGroep.rekeningGroepSoort in balansRekeningGroepSoort }
                .sortedBy { it.rekening.sortOrder }
                .map { it.toBalansDTO() }
        val mutatiesOpDatum =
            mutatiePeilDatumLijst
                .filter { it.rekening.rekeningGroep.rekeningGroepSoort in balansRekeningGroepSoort }
                .sortedBy { it.rekening.sortOrder }
                .map { it.toBalansDTO() }
        val balansOpDatum: List<Saldo.SaldoDTO> =
            balansSaldiOpDatum
                .filter { it.rekening.rekeningGroep.rekeningGroepSoort in balansRekeningGroepSoort }
                .sortedBy { it.rekening.sortOrder }
                .map { it.toBalansDTO() }
        val resultaatOpDatum =
            mutatiePeilDatumLijst
                .filter { it.rekening.rekeningGroep.rekeningGroepSoort in resultaatRekeningGroepSoort }
                .sortedBy { it.rekening.sortOrder }
                .map { it.toResultaatDTO() }
//        val budgettenOpDatum = budgetService.berekenBudgettenOpDatum(openingPeriode.gebruiker, peilDatum)
//        val geaggregeerdeBudgettenOpDatum = budgettenOpDatum
//            .groupBy { it.rekeningNaam }
//            .mapValues { it.value.reduce { acc, budgetDTO -> budgetService.add(acc, budgetDTO) } }
//            .values.toList()
        val aflossingenOpDatum =
            aflossingService.berekenAflossingenOpDatum(openingPeriode.gebruiker, openingsBalans, peilDatum.toString())
        val geaggregeerdeAflossingenOpDatum = aflossingService.aggregeerAflossingenOpDatum(aflossingenOpDatum)

//        val budgetSamenvatting: Budget.BudgetSamenvattingDTO =
//            budgetService.berekenBudgetSamenvatting(periode, peilDatum, geaggregeerdeBudgettenOpDatum, geaggregeerdeAflossingenOpDatum)
        return SaldoController.StandDTO(
            datumLaatsteBetaling = betalingRepository.findLaatsteBetalingDatumBijGebruiker(openingPeriode.gebruiker),
            periodeStartDatum = periode.periodeStartDatum,
            peilDatum = peilDatum,
            openingsBalans = openingsBalans,
            mutatiesOpDatum = mutatiesOpDatum,
            balansOpDatum = balansOpDatum,
            resultaatOpDatum = resultaatOpDatum,
//            budgetSamenvatting = budgetSamenvatting,
//            geaggregeerdeBudgettenOpDatum = geaggregeerdeBudgettenOpDatum,
//            budgettenOpDatum = budgettenOpDatum,
            aflossingenOpDatum = aflossingenOpDatum,
            geaggregeerdeAflossingenOpDatum = geaggregeerdeAflossingenOpDatum
        )
    }

    fun getOpeningSaldi(openingPeriode: Periode): List<Saldo> {
        // vul aan met 0-saldi voor missende rekeningen
        val alleRekeningen = rekeningRepository.findRekeningenVoorGebruiker(openingPeriode.gebruiker)
        val bestaandeSaldiRekeningen = saldoRepository.findAllByPeriode(openingPeriode).map { it.rekening }
        logger.debug("bestaandeSaldiRekeningen: ${bestaandeSaldiRekeningen.joinToString { it.naam }}")
        val missendeSaldiRekeningen =
            alleRekeningen.filterNot { bestaandeSaldiRekeningen.map { it.naam }.contains(it.naam) }
        logger.debug("missendeSaldiRekeningen: ${missendeSaldiRekeningen.joinToString { it.naam }}")
        val saldoLijst = missendeSaldiRekeningen.map { Saldo.SaldoDTO(0, it.naam, BigDecimal(0)) }
        return merge(openingPeriode.gebruiker, openingPeriode, saldoLijst)
    }

    fun berekenMutatieLijstOpDatum(gebruiker: Gebruiker, vanDatum: LocalDate, totDatum: LocalDate): List<Saldo> {
        val rekeningenLijst = rekeningRepository.findRekeningenVoorGebruiker(gebruiker)
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(gebruiker, vanDatum, totDatum)
        val saldoLijst = rekeningenLijst.map { rekening ->
            val mutatie =
                betalingen.fold(BigDecimal(0)) { acc, betaling -> acc + this.berekenMutaties(betaling, rekening) }
            Saldo(0, rekening, mutatie)
        }
        logger.info("mutaties van ${vanDatum} tot ${totDatum} #betalingen: ${betalingen.size}: ${saldoLijst.joinToString { "${it.rekening.naam} -> ${it.saldo}" }}")
        return saldoLijst
    }

    fun berekenMutaties(betaling: Betaling, rekening: Rekening): BigDecimal {
        return if (betaling.bron.id == rekening.id) -betaling.bedrag else BigDecimal(0) +
                if (betaling.bestemming.id == rekening.id) betaling.bedrag else BigDecimal(0)
    }

    fun berekenSaldiOpDatum(openingsSaldi: List<Saldo>, mutatieLijst: List<Saldo>): List<Saldo> {
        val saldoLijst = openingsSaldi.map { saldo: Saldo ->
            val mutatie: BigDecimal? = mutatieLijst.find { it.rekening.naam == saldo.rekening.naam }?.saldo
            saldo.fullCopy(
                saldo = saldo.saldo + (mutatie ?: BigDecimal(0))
            )
        }
        return saldoLijst
    }

    fun merge(gebruiker: Gebruiker, periode: Periode, saldoDTOs: List<Saldo.SaldoDTO>): List<Saldo> {
        val saldiBijPeriode = saldoRepository.findAllByPeriode(periode)
        val bestaandeSaldoMap: MutableMap<String, Saldo> =
            saldiBijPeriode.associateBy { it.rekening.naam }.toMutableMap()
        val nieuweSaldoList = saldoDTOs.map { saldoDTO ->
            val bestaandeSaldo = bestaandeSaldoMap[saldoDTO.rekeningNaam]
            if (bestaandeSaldo == null) {
                dto2Saldo(gebruiker, saldoDTO, periode)
            } else {
                bestaandeSaldoMap.remove(saldoDTO.rekeningNaam)
                bestaandeSaldo.fullCopy(saldo = saldoDTO.saldo)
            }
        }
        return bestaandeSaldoMap.values.toList() + nieuweSaldoList
    }

    fun dto2Saldo(gebruiker: Gebruiker, saldoDTO: Saldo.SaldoDTO, periode: Periode): Saldo {
        val rekening = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, saldoDTO.rekeningNaam).getOrNull()
            ?: run {
                logger.error("Ophalen niet bestaande rekening ${saldoDTO.rekeningNaam} voor ${gebruiker.bijnaam}.")
                throw IllegalArgumentException("Rekening ${saldoDTO.rekeningNaam} bestaat niet voor ${gebruiker.bijnaam}")
            }

        val bedrag = saldoDTO.saldo
        val saldo = saldoRepository.findOneByPeriodeAndRekening(periode, rekening)
        return if (saldo == null) {
            Saldo(0, rekening, bedrag, periode = periode)
        } else {
            saldo.fullCopy(saldo = bedrag)
        }
    }
}
