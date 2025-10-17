package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Rekening.BudgetPeriodiciteit
import io.vliet.plusmin.domain.Rekening.RekeningDTO
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMethodeRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMiddelenRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.potjesVoorNuRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.spaarPotjesRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.vastBudgetType
import io.vliet.plusmin.domain.RekeningGroep.Companion.zonderBetaalMethodenRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.RekeningGroepSoort
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
class RekeningService {
    @Autowired
    lateinit var startSaldiVanPeriodeService: StartSaldiVanPeriodeService

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var aflossingRepository: AflossingRepository

    @Autowired
    lateinit var spaartegoedRepository: SpaartegoedRepository

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var saldoRepository: SaldoRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun saveAll(gebruiker: Gebruiker, rekeningGroepLijst: List<RekeningGroep.RekeningGroepDTO>): Set<RekeningGroep> {
        val rekeningGroepen = rekeningGroepLijst
            .map { rekeningGroepDTO -> save(gebruiker, rekeningGroepDTO) }
        return rekeningGroepen.toSet()
    }

    fun save(
        gebruiker: Gebruiker,
        rekeningGroepDTO: RekeningGroep.RekeningGroepDTO,
        syscall: Boolean = false
    ): RekeningGroep {
        if (!syscall && rekeningGroepDTO.rekeningGroepSoort == RekeningGroepSoort.RESERVERING_BUFFER.name)
            throw PM_BufferRekeningImmutableException()
        val rekeningGroep = rekeningGroepRepository
            .findRekeningGroepOpNaam(gebruiker, rekeningGroepDTO.naam)
            .getOrNull() ?: RekeningGroep(
            naam = rekeningGroepDTO.naam,
            gebruiker = gebruiker,
            rekeningGroepSoort = enumValueOf<RekeningGroepSoort>(rekeningGroepDTO.rekeningGroepSoort),
            rekeningGroepIcoonNaam = rekeningGroepDTO.rekeningGroepIcoonNaam,
            sortOrder = rekeningGroepDTO.sortOrder,
            budgetType = if (rekeningGroepDTO.budgetType !== null)
                enumValueOf<RekeningGroep.BudgetType>(rekeningGroepDTO.budgetType) else null,
            rekeningen = emptyList(),
        )
        val savedRekeningGroep = rekeningGroepRepository.save(
            rekeningGroep.fullCopy(
                naam = rekeningGroepDTO.naam,
                gebruiker = gebruiker,
                rekeningGroepSoort = enumValueOf<RekeningGroepSoort>(rekeningGroepDTO.rekeningGroepSoort),
                rekeningGroepIcoonNaam = rekeningGroepDTO.rekeningGroepIcoonNaam,
                sortOrder = rekeningGroepDTO.sortOrder,
                budgetType = if (rekeningGroepDTO.budgetType !== null) enumValueOf<RekeningGroep.BudgetType>(
                    rekeningGroepDTO.budgetType
                ) else null,
            )
        )
        val rekeningen = rekeningGroepDTO.rekeningen.map { saveRekening(gebruiker, savedRekeningGroep, it) }

        if (betaalMiddelenRekeningGroepSoort.contains(rekeningGroep.rekeningGroepSoort)) {
            startSaldiVanPeriodeService.updateOpeningsReserveringsSaldo(gebruiker)
        }

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
        if (betaalMethoden.size == 0 && !zonderBetaalMethodenRekeningGroepSoort.contains(rekeningGroep.rekeningGroepSoort))
            throw PM_RekeningMoetBetaalmethodeException(listOf(rekeningDTO.naam))

        val budgetPeriodiciteit =
            if (rekeningDTO.budgetPeriodiciteit != null)
                BudgetPeriodiciteit.valueOf(rekeningDTO.budgetPeriodiciteit.uppercase())
            else null

        val gekoppeldeRekening =
            if (rekeningDTO.gekoppeldeRekening != null) rekeningRepository.findRekeningGebruikerEnNaam(
                gebruiker,
                rekeningDTO.gekoppeldeRekening
            )
            else null
        val gekoppeldeRekeningIsBetaalMiddel =
            betaalMiddelenRekeningGroepSoort.contains(gekoppeldeRekening?.rekeningGroep?.rekeningGroepSoort)
        val gekoppeldeRekeningIsSpaarRekening =
            gekoppeldeRekening?.rekeningGroep?.rekeningGroepSoort == RekeningGroepSoort.SPAARREKENING
        if ((!gekoppeldeRekeningIsBetaalMiddel && potjesVoorNuRekeningGroepSoort.contains(rekeningGroep.rekeningGroepSoort)) ||
            (!gekoppeldeRekeningIsSpaarRekening && spaarPotjesRekeningGroepSoort.contains(rekeningGroep.rekeningGroepSoort))
        )
            throw PM_PotjeMoetGekoppeldeRekeningException(listOf(rekeningDTO.naam))
        logger.info("Gevonden gekoppelde rekening: ${gekoppeldeRekening?.id} voor ${rekeningDTO.gekoppeldeRekening}")

        if (vastBudgetType.contains(rekeningGroep.budgetType) && !geldigeBetaalDag(rekeningDTO.budgetBetaalDag))
            throw PM_GeenBetaaldagException(
                listOf(
                    rekeningDTO.naam,
                    rekeningGroep.budgetType?.name ?: "null",
                    gebruiker.bijnaam
                )
            )

        val rekeningOpt = rekeningRepository.findRekeningGebruikerEnNaam(gebruiker, rekeningDTO.naam)
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
            val spaartegoed = if (rekeningGroep.budgetType == RekeningGroep.BudgetType.SPAREN) {
                if (rekeningOpt.spaartegoed == null) {
                    spaartegoedRepository.save(
                        Spaartegoed(
                            0,
                            if (rekeningDTO.spaartegoed?.doelDatum != null)
                                LocalDate.parse(
                                    rekeningDTO.spaartegoed.doelDatum,
                                    DateTimeFormatter.ISO_LOCAL_DATE
                                ) else null,
                            if (rekeningDTO.spaartegoed?.doelBedrag != null)
                                BigDecimal(rekeningDTO.spaartegoed.doelBedrag) else null,
                            rekeningDTO.spaartegoed?.notities ?: ""
                        )
                    )
                } else {
                    spaartegoedRepository.save(
                        rekeningOpt.spaartegoed.fullCopy(
                            if (rekeningDTO.spaartegoed?.doelDatum != null)
                                LocalDate.parse(
                                    rekeningDTO.spaartegoed.doelDatum,
                                    DateTimeFormatter.ISO_LOCAL_DATE
                                ) else null,
                            if (rekeningDTO.spaartegoed?.doelBedrag != null)
                                BigDecimal(rekeningDTO.spaartegoed.doelBedrag) else null,
                            rekeningDTO.spaartegoed?.notities ?: ""
                        )
                    )
                }
            } else null
            rekeningRepository.save(
                rekeningOpt.fullCopy(
                    rekeningGroep = rekeningGroep,
                    sortOrder = rekeningDTO.sortOrder ?: 0,
                    bankNaam = rekeningDTO.bankNaam,
                    budgetBetaalDag = rekeningDTO.budgetBetaalDag,
                    budgetAanvulling = rekeningDTO.budgetAanvulling,
                    gekoppeldeRekening = gekoppeldeRekening,
                    budgetPeriodiciteit = budgetPeriodiciteit,
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

            val savedRekening = rekeningRepository.save(
                Rekening(
                    naam = rekeningDTO.naam,
                    rekeningGroep = rekeningGroep,
                    sortOrder = rekeningDTO.sortOrder ?: 0,
                    bankNaam = rekeningDTO.bankNaam,
                    budgetBetaalDag = rekeningDTO.budgetBetaalDag,
                    budgetAanvulling = rekeningDTO.budgetAanvulling,
                    gekoppeldeRekening = gekoppeldeRekening,
                    budgetPeriodiciteit = budgetPeriodiciteit,
                    budgetBedrag = rekeningDTO.budgetBedrag,
                    budgetVariabiliteit = rekeningDTO.budgetVariabiliteit,
                    maanden = rekeningDTO.maanden,
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing,
                    spaartegoed = null,
                )
            )

            savedRekening
        }
        val periode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
        val saldoOpt = saldoRepository.findOneByPeriodeAndRekening(periode, rekening)
        if (saldoOpt == null) {
            saldoRepository.save(
                Saldo(
                    id = 0,
                    rekening = rekening,
                    openingsBalansSaldo = rekeningDTO.saldo ?: BigDecimal.ZERO,
                    openingsReserveSaldo = rekeningDTO.reserve ?: BigDecimal.ZERO,
                    periode = periode,
                    achterstand = BigDecimal.ZERO,
                    budgetMaandBedrag = BigDecimal.ZERO,
                    betaling = BigDecimal.ZERO
                )
            )
        } else {
            saldoRepository.save(
                saldoOpt.fullCopy(
                    openingsBalansSaldo = rekeningDTO.saldo ?: BigDecimal.ZERO,
                    openingsReserveSaldo = rekeningDTO.reserve ?: BigDecimal.ZERO,
                )
            )
        }
        logger.info(
            "Opslaan rekening ${rekening.naam} voor ${gebruiker.bijnaam} en periodiciteit ${rekening.budgetPeriodiciteit} met bedrag ${rekening.budgetBedrag} " +
                    "openingsBalansSaldo: ${rekeningDTO.saldo ?: BigDecimal.ZERO}, openingsReserveSaldo: ${rekeningDTO.reserve ?: BigDecimal.ZERO}."
        )
        return rekening
    }

    fun geldigeBetaalDag(dag: Int?): Boolean {
        return (dag != null && (dag in 1..28))
    }

    fun rekeningGroepenPerBetalingsSoort(
        gebruiker: Gebruiker,
        periode: Periode
    ): List<RekeningGroep.RekeningGroepPerBetalingsSoort> {
        val rekeningGroepenMetGeldigeRekeningen = findRekeningGroepenMetGeldigeRekeningen(gebruiker, periode)
        return RekeningGroep.betaalSoort2RekeningGroepSoort.map { (betalingsSoort, rekeningGroepSoort) ->
            RekeningGroep.RekeningGroepPerBetalingsSoort(
                betalingsSoort = betalingsSoort,
                rekeningGroepen = rekeningGroepenMetGeldigeRekeningen
                    .map { it.toDTO(periode) }
                    .filter { it.rekeningGroepSoort == rekeningGroepSoort.name }
                    .sortedBy { it.sortOrder }
            )
        }.filter { it.rekeningGroepen.isNotEmpty() }
    }

    fun findRekeningGroepenMetGeldigeRekeningen(
        gebruiker: Gebruiker,
        periode: Periode
    ): List<RekeningGroep> {
        return rekeningGroepRepository.findRekeningGroepenVoorGebruiker(gebruiker)
            .map { rekeningGroep ->
                rekeningGroep.fullCopy(
                    rekeningen = rekeningGroep.rekeningen
                        .filter { it.rekeningIsGeldigInPeriode(periode) }
                        .sortedBy { it.sortOrder }
                )
            }.filter { it.rekeningen.isNotEmpty() }
    }
}