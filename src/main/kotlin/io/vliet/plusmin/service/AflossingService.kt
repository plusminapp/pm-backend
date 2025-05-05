package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Aflossing.AflossingDTO
import io.vliet.plusmin.domain.Budget.BudgetDTO
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

@Service
class AflossingService {
    @Autowired
    lateinit var aflossingRepository: AflossingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

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
            val maxSortOrderOpt = rekeningRepository.findMaxSortOrder()
            val maxSortOrder =
                if (maxSortOrderOpt != null)
                    maxSortOrderOpt.sortOrder + 100
                else 10
            val rekening = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, aflossingDTO.rekening.naam)
                ?: rekeningRepository.save(
                    Rekening(
                        gebruiker = gebruiker,
                        rekeningSoort = Rekening.RekeningSoort.AFLOSSING,
                        naam = aflossingDTO.rekening.naam,
                        sortOrder = maxSortOrder
                    )
                )
            if (rekening.rekeningSoort != Rekening.RekeningSoort.AFLOSSING) {
                val message =
                    "Rekening ${aflossingDTO.rekening} voor ${gebruiker.bijnaam} heeft rekeningsoort ${rekening.rekeningSoort} en kan dus geen aflossing koppelen."
                logger.error(message)
                throw DataIntegrityViolationException(message)
            }
            val aflossing = aflossingRepository.findAflossingVoorRekeningNaam(gebruiker, aflossingDTO.rekening.naam)
                ?.fullCopy(
                    rekening = rekening,
                    startDatum = LocalDate.parse(aflossingDTO.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindDatum = LocalDate.parse(aflossingDTO.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindBedrag = aflossingDTO.eindBedrag.toBigDecimal(),
                    aflossingsBedrag = aflossingDTO.aflossingsBedrag.toBigDecimal(),
                    betaalDag = aflossingDTO.betaalDag,
                    dossierNummer = aflossingDTO.dossierNummer,
                    notities = aflossingDTO.notities,
                )
                ?: Aflossing(
                    rekening = rekening,
                    startDatum = LocalDate.parse(aflossingDTO.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindDatum = LocalDate.parse(aflossingDTO.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                    eindBedrag = aflossingDTO.eindBedrag.toBigDecimal(),
                    aflossingsBedrag = aflossingDTO.aflossingsBedrag.toBigDecimal(),
                    betaalDag = aflossingDTO.betaalDag,
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
                        rekening = aflossing.rekening,
                        bedrag = -berekenAflossingBedragOpDatum(
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
                    aflossing.aflossingsBedrag else BigDecimal(0)
                val saldoStartPeriode = getBalansVanStand(balansOpDatum, aflossing.rekening)
                val deltaStartPeriode =
                    saldoStartPeriode - berekenAflossingBedragOpDatum(
                        aflossing,
                        gekozenPeriode.periodeStartDatum.minusDays(1)
                    )
                val aflossingMoetBetaaldZijn = periodeService.berekenDagInPeriode(
                    aflossing.betaalDag,
                    gekozenPeriode
                ) < peilDatum
                val aflossingBetaling = getBetalingVoorAflossingInPeriode(aflossing, gekozenPeriode)
                val actueleAchterstand =
                    (if (aflossingMoetBetaaldZijn) aflossing.aflossingsBedrag else BigDecimal(0)) +
                            deltaStartPeriode - aflossingBetaling
                val betaaldBinnenAflossing = aflossingBetaling.min(
                    (if (aflossingMoetBetaaldZijn) aflossing.aflossingsBedrag else BigDecimal(0)) + deltaStartPeriode
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
                    meerDanVerwacht = if (!aflossingMoetBetaaldZijn && actueleAchterstand < BigDecimal(0))
                        -actueleAchterstand else BigDecimal(0),
                    minderDanVerwacht = actueleAchterstand,
                    meerDanMaandAflossing = if (aflossingMoetBetaaldZijn && actueleAchterstand < BigDecimal(0))
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
        return if (saldo == null) BigDecimal(0) else -saldo.bedrag
    }

    fun ishetAlAfgeschreven(aflossing: Aflossing, periode: Periode, peilDatum: LocalDate): Boolean {
        val datumDatHetWordtAfgeschreven = if (periode.periodeStartDatum.dayOfMonth <= aflossing.betaalDag) {
            periode.periodeStartDatum.withDayOfMonth(aflossing.betaalDag)
        } else {
            periode.periodeStartDatum.withDayOfMonth(aflossing.betaalDag).plusMonths(1)
        }
        return peilDatum > datumDatHetWordtAfgeschreven
    }

    fun berekenAflossingBedragOpDatum(aflossing: Aflossing, peilDatum: LocalDate): BigDecimal {
        if (peilDatum < aflossing.startDatum || peilDatum.withDayOfMonth(aflossing.betaalDag) < aflossing.startDatum)
            return aflossing.eindBedrag
        if (peilDatum > aflossing.eindDatum) return BigDecimal(0)
        val isHetAlAfgeschreven = if (peilDatum.dayOfMonth <= aflossing.betaalDag) 0 else 1
        val aantalMaanden = ChronoUnit.MONTHS.between(aflossing.startDatum, peilDatum) + isHetAlAfgeschreven
        logger.warn("berekenAflossingDTOOpDatum ${aflossing.startDatum} -> ${peilDatum} = ${aantalMaanden}: ${peilDatum.dayOfMonth} - ${aflossing.betaalDag} ${peilDatum.dayOfMonth < aflossing.betaalDag} ${isHetAlAfgeschreven}")
        return aflossing.eindBedrag - BigDecimal(aantalMaanden) * aflossing.aflossingsBedrag
    }

    fun getBetalingVoorAflossingInPeriode(aflossing: Aflossing, periode: Periode): BigDecimal {
        val betalingen = betalingRepository.findAllByGebruikerTussenDatums(
            aflossing.rekening.gebruiker,
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
            aflossingsBedrag = aflossing1.aflossingsBedrag.toBigDecimal().plus(aflossing2.aflossingsBedrag.toBigDecimal()).toString(),
            aflossingOpPeilDatum = aflossing1.aflossingOpPeilDatum?.plus(aflossing2.aflossingOpPeilDatum ?: BigDecimal(0)),
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