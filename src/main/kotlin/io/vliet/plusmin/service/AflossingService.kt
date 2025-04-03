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
                val saldoStartPeriode = getBalansVanStand(balansOpDatum, aflossing.rekening)
                val deltaStartPeriode =
                    berekenAflossingBedragOpDatum(
                        aflossing,
                        gekozenPeriode.periodeStartDatum.minusDays(1)
                    ) - saldoStartPeriode

                aflossing.toDTO(
                    aflossingPeilDatum = peilDatumAsString,
                    saldoStartPeriode = saldoStartPeriode,
                    deltaStartPeriode = deltaStartPeriode,
                    aflossingBetaling = getBetalingVoorAflossingInPeriode(aflossing, gekozenPeriode)
                )
            }
    }

    fun getBalansVanStand(balansOpDatum: List<Saldo.SaldoDTO>, rekening: Rekening): BigDecimal {
        val saldo = balansOpDatum.find { it.rekeningNaam == rekening.naam }
        return if (saldo == null) BigDecimal(0) else -saldo.bedrag
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
}