package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Aflossing.AflossingDTO
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.jvm.optionals.getOrNull

@Service
class AflossingService {
    @Autowired
    lateinit var aflossingRepository: AflossingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    @Autowired
    lateinit var betalingRepository: BetalingRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Transactional
    fun creeerAflossingen(gebruiker: Gebruiker, aflossingenLijst: List<AflossingDTO>) {
        aflossingenLijst.map { aflossingDTO ->
            val maxSortOrder = rekeningRepository.findMaxSortOrder().getOrNull()?.sortOrder ?: 0
            val rekeningGroep = rekeningGroepRepository
                .findRekeningGroepVoorGebruiker(gebruiker, aflossingDTO.rekening.rekeningGroepNaam!!)
                .getOrNull()
                ?: throw DataIntegrityViolationException("Geen rekeninggroep voor gebruiker ${gebruiker.bijnaam} en rekeninggroep ${aflossingDTO.rekening.rekeningGroepNaam}")
            val rekening =
                rekeningRepository.findRekeningOpGroepEnNaam(rekeningGroep, aflossingDTO.rekening.naam).getOrNull()
                    ?: rekeningRepository.save(
                        Rekening(
                            rekeningGroep = rekeningGroep,
                            naam = aflossingDTO.rekening.naam,
                            sortOrder = maxSortOrder + 100,
                            budgetPeriodiciteit = Rekening.BudgetPeriodiciteit.MAAND,
                            budgetBedrag = aflossingDTO.rekening.budgetBedrag,
                            budgetBetaalDag = aflossingDTO.rekening.budgetBetaalDag,
                            vanPeriode = aflossingDTO.rekening.vanPeriode,
                            totEnMetPeriode = aflossingDTO.rekening.totEnMetPeriode,
                        )
                    )
            if (rekening.rekeningGroep.rekeningGroepSoort != RekeningGroep.RekeningGroepSoort.AFLOSSING) {
                val message =
                    "Rekening ${aflossingDTO.rekening} voor ${gebruiker.bijnaam} heeft rekeningsoort ${rekening.rekeningGroep.rekeningGroepSoort} en kan dus geen aflossing koppelen."
                logger.error(message)
                throw DataIntegrityViolationException(message)
            }
            val aflossing = aflossingRepository.findAflossingVoorRekeningNaam(gebruiker, aflossingDTO.rekening.naam)
                ?.fullCopy(
                    rekening = rekening,
                    startDatum = LocalDate.parse(aflossingDTO.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindDatum = LocalDate.parse(aflossingDTO.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindBedrag = aflossingDTO.eindBedrag.toBigDecimal(),
                    dossierNummer = aflossingDTO.dossierNummer,
                    notities = aflossingDTO.notities,
                )
                ?: Aflossing(
                    rekening = rekening,
                    startDatum = LocalDate.parse(aflossingDTO.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindDatum = LocalDate.parse(aflossingDTO.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindBedrag = aflossingDTO.eindBedrag.toBigDecimal(),
                    dossierNummer = aflossingDTO.dossierNummer,
                    notities = aflossingDTO.notities,
                )
            val verwachteEindBedrag = berekenAflossingBedragOpDatum(aflossing, aflossing.eindDatum)
            if (verwachteEindBedrag != aflossing.eindBedrag) {
                logger.warn("Aflossing ${aflossing.rekening.naam} verwachte ${verwachteEindBedrag} maar in Aflossing ${aflossing.eindBedrag}")
            }
            aflossingRepository.save(aflossing)
            logger.info("Aflossing ${aflossingDTO.rekening.naam} voor ${gebruiker.bijnaam} opgeslagen.")
            val periode = periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
            if (periode != null) {
                saldoRepository.save(
                    Saldo(
                        rekeningGroep = aflossing.rekening.rekeningGroep,
                        rekening = aflossing.rekening,
                        saldo = -berekenAflossingBedragOpDatum(
                            aflossing,
                            periode.periodeStartDatum
                        ),
                        periode = periode
                    )
                )
            }
        }
    }

    fun berekenAflossingenOpDatum(
        gebruiker: Gebruiker,
        balansOpDatum: List<Saldo.SaldoDTO>,
        peilDatumAsString: String
    ): List<AflossingDTO> {
        val aflossingenLijst = aflossingRepository.findAflossingenVoorGebruiker(gebruiker)
        val peilDatum = LocalDate.parse(peilDatumAsString, DateTimeFormatter.ISO_LOCAL_DATE)
        val saldoPeriode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val gekozenPeriode = periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, peilDatum) ?: run {
            logger.error("Geen periode voor ${gebruiker.bijnaam} op ${peilDatum}, gebruik ${saldoPeriode.periodeStartDatum}")
            saldoPeriode
        }
        return aflossingenLijst
            .sortedBy { it.rekening.sortOrder }
            .map { aflossing ->
                val aflossingOpPeilDatum = if (ishetAlAfgeschreven(aflossing, gekozenPeriode, peilDatum))
                    aflossing.rekening.budgetBedrag else BigDecimal(0)
                val saldoStartPeriode = getBalansVanStand(balansOpDatum, aflossing.rekening)
                val deltaStartPeriode =
                    saldoStartPeriode - berekenAflossingBedragOpDatum(
                        aflossing,
                        gekozenPeriode.periodeStartDatum.minusDays(1)
                    )
                val aflossingMoetBetaaldZijn = periodeService.berekenDagInPeriode(
                    aflossing.rekening.budgetBetaalDag
                        ?: throw DataIntegrityViolationException("Geen betaaldag voor aflossing ${aflossing.rekening.naam} gebruiker ${gebruiker.bijnaam}"),
                    gekozenPeriode
                ) < peilDatum
                val aflossingBetaling = getBetalingVoorAflossingInPeriode(aflossing, gekozenPeriode)
                val actueleAchterstand =
                    (if (aflossingMoetBetaaldZijn) aflossing.rekening.budgetBedrag else BigDecimal(0))?.plus(
                        deltaStartPeriode
                    )?.minus(aflossingBetaling)
                val betaaldBinnenAflossing = aflossingBetaling.min(
                    (if (aflossingMoetBetaaldZijn) aflossing.rekening.budgetBedrag else BigDecimal(0))?.plus(
                        deltaStartPeriode
                    )
                )
                aflossing.toDTO(
                    aflossingPeilDatum = peilDatumAsString,
                    aflossingOpPeilDatum = aflossingOpPeilDatum,
                    saldoStartPeriode = saldoStartPeriode,
                    deltaStartPeriode = deltaStartPeriode,
                    aflossingBetaling = aflossingBetaling,
                    aflossingMoetBetaaldZijn = aflossingMoetBetaaldZijn,
                    actueleStand = saldoStartPeriode - aflossingBetaling,
                    actueleAchterstand = actueleAchterstand,
                    betaaldBinnenAflossing = betaaldBinnenAflossing,
                    meerDanVerwacht = if (!aflossingMoetBetaaldZijn && actueleAchterstand!! < BigDecimal(0))
                        -actueleAchterstand else BigDecimal(0),
                    minderDanVerwacht = actueleAchterstand,
                    meerDanMaandAflossing = if (aflossingMoetBetaaldZijn && actueleAchterstand!! < BigDecimal(0))
                        -actueleAchterstand else BigDecimal(0),
                )
            }
    }

    fun aggregeerAflossingenOpDatum(
        aflossingDtoLijst: List<AflossingDTO>,
    ): AflossingDTO? {
        if (aflossingDtoLijst.isEmpty()) return null
        return aflossingDtoLijst
            .reduce { acc, aflossingDTO -> add(acc, aflossingDTO) }

    }

    fun getBalansVanStand(balansOpDatum: List<Saldo.SaldoDTO>, rekening: Rekening): BigDecimal {
        val saldo = balansOpDatum.find { it.rekeningNaam == rekening.naam }
        return if (saldo == null) BigDecimal(0) else -saldo.saldo
    }

    fun ishetAlAfgeschreven(aflossing: Aflossing, periode: Periode, peilDatum: LocalDate): Boolean {
        val betaalDag = aflossing.rekening.budgetBetaalDag
            ?: throw DataIntegrityViolationException("Geen betaaldag voor aflossing ${aflossing.rekening.naam} gebruiker ${periode.gebruiker.bijnaam}")
        val datumDatHetWordtAfgeschreven = if (periode.periodeStartDatum.dayOfMonth <= betaalDag) {
            periode.periodeStartDatum.withDayOfMonth(betaalDag)
        } else {
            periode.periodeStartDatum.withDayOfMonth(betaalDag).plusMonths(1)
        }
        return peilDatum > datumDatHetWordtAfgeschreven
    }

    fun berekenAflossingBedragOpDatum(aflossing: Aflossing, peilDatum: LocalDate): BigDecimal {
        val betaalDag = aflossing.rekening.budgetBetaalDag
            ?: throw DataIntegrityViolationException("Geen betaaldag voor aflossing ${aflossing.rekening.naam} gebruiker ${aflossing.rekening.rekeningGroep.gebruiker.bijnaam}")

        if (peilDatum < aflossing.startDatum || peilDatum.withDayOfMonth(betaalDag) < aflossing.startDatum)
            return aflossing.eindBedrag
        if (peilDatum > aflossing.eindDatum) return BigDecimal(0)
        val isHetAlAfgeschreven = if (peilDatum.dayOfMonth <= betaalDag) 0 else 1
        val aantalMaanden = ChronoUnit.MONTHS.between(aflossing.startDatum, peilDatum) + isHetAlAfgeschreven
        logger.warn("berekenAflossingDTOOpDatum ${aflossing.startDatum} -> ${peilDatum} = ${aantalMaanden}: ${peilDatum.dayOfMonth} - ${betaalDag} ${peilDatum.dayOfMonth < betaalDag} ${isHetAlAfgeschreven}")
        return aflossing.eindBedrag - (aflossing.rekening.budgetBedrag?.let { BigDecimal(aantalMaanden) * it } ?: BigDecimal.ZERO)    }

    fun getBetalingVoorAflossingInPeriode(aflossing: Aflossing, periode: Periode): BigDecimal {
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(
            aflossing.rekening.rekeningGroep.gebruiker,
            periode.periodeStartDatum,
            periode.periodeEindDatum
        )
        val filteredBetalingen =
            betalingen.filter { it.bron.id == aflossing.rekening.id || it.bestemming.id == aflossing.rekening.id }
        val bedrag =
            filteredBetalingen.fold(BigDecimal(0)) { acc, betaling -> if (betaling.bron.id == aflossing.rekening.id) acc - betaling.bedrag else acc + betaling.bedrag }
        return bedrag
    }

    fun add(aflossing1: AflossingDTO, aflossing2: AflossingDTO): AflossingDTO {
        return aflossing1.fullCopy(
            eindBedrag = aflossing1.eindBedrag.toBigDecimal().plus(aflossing2.eindBedrag.toBigDecimal()).toString(),
//            aflossingsBedrag = aflossing1.rekening.budgetBedrag
//                .plus(aflossing2.rekening.budgetBedrag).toString(),
            aflossingOpPeilDatum = aflossing1.aflossingOpPeilDatum?.plus(
                aflossing2.aflossingOpPeilDatum ?: BigDecimal(0)
            ),
            aflossingBetaling = aflossing1.aflossingBetaling?.plus(aflossing2.aflossingBetaling ?: BigDecimal(0)),
            deltaStartPeriode = aflossing1.deltaStartPeriode?.plus(aflossing2.deltaStartPeriode ?: BigDecimal(0)),
            saldoStartPeriode = aflossing1.saldoStartPeriode?.plus(aflossing2.saldoStartPeriode ?: BigDecimal(0)),
            aflossingMoetBetaaldZijn = null,
            actueleStand = aflossing1.actueleStand?.plus(aflossing2.actueleStand ?: BigDecimal(0)),
            betaaldBinnenAflossing = aflossing1.betaaldBinnenAflossing?.plus(
                aflossing2.betaaldBinnenAflossing ?: BigDecimal(0)
            ),
            meerDanVerwacht = aflossing1.meerDanVerwacht?.plus(aflossing2.meerDanVerwacht ?: BigDecimal(0)),
            minderDanVerwacht = aflossing1.minderDanVerwacht?.plus(aflossing2.minderDanVerwacht ?: BigDecimal(0)),
            meerDanMaandAflossing = aflossing1.meerDanMaandAflossing?.plus(
                aflossing2.meerDanMaandAflossing ?: BigDecimal(0)
            ),
        )
    }

}