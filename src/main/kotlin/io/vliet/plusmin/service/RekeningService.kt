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
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(gebruiker: Gebruiker, rekeningGroepLijst: List<RekeningGroep.RekeningGroepDTO>): Set<RekeningGroep> {
        val rekeningGroepLijst = rekeningGroepLijst
            .map { rekeningGroepDTO -> save(gebruiker, rekeningGroepDTO) }
        return rekeningGroepLijst.toSet()
    }

    fun save(gebruiker: Gebruiker, rekeningGroepDTO: RekeningGroep.RekeningGroepDTO): RekeningGroep {
        val rekeningGroep = rekeningGroepRepository
            .findRekeningGroepVoorGebruiker(gebruiker, rekeningGroepDTO.naam)
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
            rekeningen = emptyList(),
        ))
        val rekeningen = rekeningGroepDTO.rekeningen.map { saveRekening(gebruiker, savedRekeningGroep, it) }
        return rekeningGroep.fullCopy(rekeningen = rekeningen)
    }

    fun saveRekening(gebruiker: Gebruiker, rekeningGroep: RekeningGroep, rekeningDTO: RekeningDTO): Rekening {
        val betaalMethoden =
            rekeningDTO.betaalMethoden.mapNotNull {
                rekeningRepository.findRekeningGebruikerEnNaam(
                    gebruiker,
                    it.naam
                ).getOrNull()
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
                            LocalDate.parse(rekeningDTO.aflossing.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.aflossing.eindBedrag),
                            rekeningDTO.aflossing.dossierNummer,
                            rekeningDTO.aflossing.notities
                        )
                    )
                } else {
                    aflossingRepository.save(
                        rekeningOpt.aflossing!!.fullCopy(
                            LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            LocalDate.parse(rekeningDTO.aflossing.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.aflossing.eindBedrag),
                            rekeningDTO.aflossing.dossierNummer,
                            rekeningDTO.aflossing.notities
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
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing ?: rekeningOpt.aflossing,
                )
            )
        } else {

            val aflossing = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.AFLOSSING) {
                aflossingRepository.save(
                    Aflossing(
                        0,
                        LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                        LocalDate.parse(rekeningDTO.aflossing.eindDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                        BigDecimal(rekeningDTO.aflossing.eindBedrag),
                        rekeningDTO.aflossing.dossierNummer,
                        rekeningDTO.aflossing.notities
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
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing,
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
                    saldo = rekeningDTO.saldo ?: BigDecimal(0),
                    periode = periode,
                    achterstand = BigDecimal(0),
                    budgetMaandBedrag = BigDecimal(0),
                    budgetBetaling = BigDecimal(0)
                )
            )
        }
        return rekening
    }

    fun rekeningIsGeldigInPeriode(rekening: Rekening, periode: Periode): Boolean {
        return (rekening.vanPeriode == null || periode.periodeStartDatum >= rekening.vanPeriode.periodeStartDatum) &&
                (rekening.totEnMetPeriode == null || periode.periodeEindDatum <= rekening.totEnMetPeriode.periodeEindDatum)
    }


    fun findRekeningGroepenMetGeldigeRekeningen(
        gebruiker: Gebruiker,
        periode: Periode
    ): List<RekeningGroep.RekeningGroepDTO> {
        val rekeningGroepenLijst = rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
        return rekeningGroepenLijst.map { rekeningGroep ->
            rekeningGroep.fullCopy(
                rekeningen = rekeningGroep.rekeningen
                    .filter { rekeningIsGeldigInPeriode(it, periode) }
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