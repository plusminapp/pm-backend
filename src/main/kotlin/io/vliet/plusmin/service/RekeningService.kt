package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Rekening.BudgetPeriodiciteit
import io.vliet.plusmin.domain.Rekening.RekeningDTO
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMethodeRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.RekeningGroepSoort
import io.vliet.plusmin.repository.AflossingRepository
import io.vliet.plusmin.repository.PeriodeRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.repository.SaldoRepository
import io.vliet.plusmin.repository.SpaartegoedRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull

@Service
class RekeningService {
    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var aflossingRepository: AflossingRepository

    @Autowired
    lateinit var spaartegoedRepository: SpaartegoedRepository

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(gebruiker: Gebruiker, rekeningGroepLijst: List<RekeningGroep.RekeningGroepDTO>): Set<RekeningGroep> {
        val rekeningGroepen = rekeningGroepLijst
            .map { rekeningGroepDTO -> save(gebruiker, rekeningGroepDTO) }
        return rekeningGroepen.toSet()
    }

    fun save(gebruiker: Gebruiker, rekeningGroepDTO: RekeningGroep.RekeningGroepDTO): RekeningGroep {
        val rekeningGroep = rekeningGroepRepository
            .findRekeningGroepOpNaam(gebruiker, rekeningGroepDTO.naam)
            .getOrNull() ?: RekeningGroep(
            naam = rekeningGroepDTO.naam,
            gebruiker = gebruiker,
            rekeningGroepSoort = enumValueOf<RekeningGroepSoort>(rekeningGroepDTO.rekeningGroepSoort),
            rekeningGroepIcoonNaam = rekeningGroepDTO.rekeningGroepIcoonNaam,
            sortOrder = rekeningGroepDTO.sortOrder,
            budgetType = if (rekeningGroepDTO.budgetType !== null) enumValueOf<RekeningGroep.BudgetType>(
                rekeningGroepDTO.budgetType
            ) else null,
            rekeningen = emptyList(),
            )
        val savedRekeningGroep = rekeningGroepRepository.save(rekeningGroep.fullCopy(
            naam = rekeningGroepDTO.naam,
            gebruiker = gebruiker,
            rekeningGroepSoort = enumValueOf<RekeningGroepSoort>(rekeningGroepDTO.rekeningGroepSoort),
            rekeningGroepIcoonNaam = rekeningGroepDTO.rekeningGroepIcoonNaam,
            sortOrder = rekeningGroepDTO.sortOrder,
            budgetType = if (rekeningGroepDTO.budgetType !== null) enumValueOf<RekeningGroep.BudgetType>(
                rekeningGroepDTO.budgetType
            ) else null,
        ))
        val rekeningen = rekeningGroepDTO.rekeningen.map { saveRekening(gebruiker, savedRekeningGroep, it) }
        return rekeningGroep.fullCopy(rekeningen = rekeningen)
    }

    fun saveRekening(gebruiker: Gebruiker, rekeningGroep: RekeningGroep, rekeningDTO: RekeningDTO): Rekening {
        logger.info("Opslaan rekening ${rekeningDTO.naam} voor ${gebruiker.bijnaam} in groep ${rekeningGroep.naam}.")
        val betaalMethoden =
            rekeningDTO.betaalMethoden.mapNotNull {
                rekeningRepository.findRekeningGebruikerEnNaam(
                    gebruiker,
                    it.naam
                )
            }.filter { betaalMethodeRekeningGroepSoort.contains(it.rekeningGroep.rekeningGroepSoort) }
        val rekeningOpt = rekeningRepository.findRekeningOpGroepEnNaam(rekeningGroep, rekeningDTO.naam)
            .getOrNull()
        val rekening = if (rekeningOpt != null) {
            logger.info("Rekening bestaat al: ${rekeningOpt.naam} met id ${rekeningOpt.id} voor ${gebruiker.bijnaam}")
            val aflossing = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.AFLOSSING) {
                if (rekeningOpt.aflossing == null) {
                    aflossingRepository.save(
                        Aflossing(
                            0,
                            LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.aflossing.eindBedrag),
                            rekeningDTO.aflossing.dossierNummer,
                            rekeningDTO.aflossing.notities
                        )
                    )
                } else {
                    aflossingRepository.save(
                        rekeningOpt.aflossing.fullCopy(
                            LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.aflossing.eindBedrag),
                            rekeningDTO.aflossing.dossierNummer,
                            rekeningDTO.aflossing.notities
                        )
                    )
                }
            } else null
            val spaartegoed = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.SPAARTEGOED) {
                if (rekeningOpt.spaartegoed == null) {
                    spaartegoedRepository.save(
                        Spaartegoed(
                            0,
                            LocalDate.parse(rekeningDTO.spaartegoed!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.spaartegoed.eindBedrag),
                            rekeningDTO.spaartegoed.notities
                        )
                    )
                } else {
                    spaartegoedRepository.save(
                        rekeningOpt.spaartegoed.fullCopy(
                            LocalDate.parse(rekeningDTO.spaartegoed!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.spaartegoed.eindBedrag),
                            rekeningDTO.spaartegoed.notities
                        )
                    )
                }
            } else null
            rekeningRepository.save(
                rekeningOpt.fullCopy(
                    rekeningGroep = rekeningGroep,
                    sortOrder = rekeningDTO.sortOrder,
                    bankNaam = rekeningDTO.bankNaam,
                    budgetBetaalDag = rekeningDTO.budgetBetaalDag,
                    budgetPeriodiciteit = if (rekeningDTO.budgetPeriodiciteit != null)
                        BudgetPeriodiciteit.valueOf(rekeningDTO.budgetPeriodiciteit)
                    else null,
                    budgetBedrag = rekeningDTO.budgetBedrag,
                    budgetVariabiliteit = rekeningDTO.budgetVariabiliteit,
                    maanden = rekeningDTO.maanden,
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing ?: rekeningOpt.aflossing,
                    spaartegoed = spaartegoed ?: rekeningOpt.spaartegoed,
                )
            )
        } else {

            val aflossing = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.AFLOSSING) {
                aflossingRepository.save(
                    Aflossing(
                        0,
                        LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                        BigDecimal(rekeningDTO.aflossing.eindBedrag),
                        rekeningDTO.aflossing.dossierNummer,
                        rekeningDTO.aflossing.notities
                    )
                )
            } else null

            val spaartegoed = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.SPAARTEGOED) {
                spaartegoedRepository.save(
                    Spaartegoed(
                        0,
                        LocalDate.parse(rekeningDTO.spaartegoed!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                        BigDecimal(rekeningDTO.spaartegoed.eindBedrag),
                        rekeningDTO.spaartegoed.notities
                    )
                )
            } else null

            val savedRekening = rekeningRepository.save(
                Rekening(
                    naam = rekeningDTO.naam,
                    rekeningGroep = rekeningGroep,
                    sortOrder = rekeningDTO.sortOrder,
                    bankNaam = rekeningDTO.bankNaam,
                    budgetBetaalDag = rekeningDTO.budgetBetaalDag,
                    budgetPeriodiciteit = if (rekeningDTO.budgetPeriodiciteit != null)
                        BudgetPeriodiciteit.valueOf(rekeningDTO.budgetPeriodiciteit.uppercase())
                    else null,
                    budgetBedrag = rekeningDTO.budgetBedrag,
                    budgetVariabiliteit = rekeningDTO.budgetVariabiliteit,
                    maanden = rekeningDTO.maanden,
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing,
                    spaartegoed = spaartegoed,
                )
            )

            savedRekening
        }
        logger.info("Opslaan rekening ${rekening.naam} voor ${gebruiker.bijnaam} en periodiciteit ${rekening.budgetPeriodiciteit} met bedrag ${rekening.budgetBedrag}; banknaam ${rekeningDTO.bankNaam}.")
        if (rekeningOpt == null) {
            val periode = periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
            saldoRepository.save(
                Saldo(
                    id = 0,
                    rekening = rekening,
                    openingsSaldo = rekeningDTO.saldo ?: BigDecimal.ZERO,
                    periode = periode,
                    achterstand = BigDecimal.ZERO,
                    budgetMaandBedrag = BigDecimal.ZERO,
                    budgetBetaling = BigDecimal.ZERO
                )
            )
        }
        return rekening
    }

    fun findRekeningGroepenMetGeldigeRekeningen(
        gebruiker: Gebruiker,
        periode: Periode
    ): List<RekeningGroep.RekeningGroepDTO> {
        val rekeningGroepenLijst = rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
        return rekeningGroepenLijst.map { rekeningGroep ->
            rekeningGroep.fullCopy(
                rekeningen = rekeningGroep.rekeningen
                    .filter { it.rekeningIsGeldigInPeriode( periode) }
                    .sortedBy { it.sortOrder }
            ).toDTO(periode)
        }.filter { it.rekeningen.isNotEmpty() }
    }

    fun rekeningenPerBetalingsSoort(
        rekeningGroepen: List<RekeningGroep.RekeningGroepDTO>
    ): List<RekeningGroep.RekeningGroepPerBetalingsSoort> {
        return RekeningGroep.betaalSoort2RekeningGroepSoort.map { (betalingsSoort, rekeningGroepSoort) ->
            RekeningGroep.RekeningGroepPerBetalingsSoort(
                betalingsSoort = betalingsSoort,
                rekeningGroepen = rekeningGroepen
                    .filter { it.rekeningGroepSoort == rekeningGroepSoort.name }
                    .sortedBy { it.sortOrder }
            )
        }.filter { it.rekeningGroepen.isNotEmpty() }
    }
}