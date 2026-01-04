package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.domain.Rekening.BudgetPeriodiciteit
import io.vliet.plusmin.domain.Rekening.RekeningDTO
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMethodeRekeningGroepSoort
import io.vliet.plusmin.domain.RekeningGroep.Companion.betaalMiddelenRekeningGroepSoort
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
    lateinit var reserveringService: ReserveringService

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

    fun saveAll(administratie: Administratie, rekeningGroepLijst: List<RekeningGroep.RekeningGroepDTO>): Set<RekeningGroep> {
        val rekeningGroepen = rekeningGroepLijst
            .map { rekeningGroepDTO -> save(administratie, rekeningGroepDTO) }
        return rekeningGroepen.toSet()
    }

    fun save(
        administratie: Administratie,
        rekeningGroepDTO: RekeningGroep.RekeningGroepDTO,
        syscall: Boolean = false
    ): RekeningGroep {
        if (!syscall && rekeningGroepDTO.rekeningGroepSoort == RekeningGroepSoort.RESERVERING_BUFFER.name)
            throw PM_BufferRekeningImmutableException()
        val rekeningGroep = rekeningGroepRepository
            .findRekeningGroepOpNaam(administratie, rekeningGroepDTO.naam)
            .getOrNull() ?: RekeningGroep(
            naam = rekeningGroepDTO.naam,
            administratie = administratie,
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
                administratie = administratie,
                rekeningGroepSoort = enumValueOf<RekeningGroepSoort>(rekeningGroepDTO.rekeningGroepSoort),
                rekeningGroepIcoonNaam = rekeningGroepDTO.rekeningGroepIcoonNaam,
                sortOrder = rekeningGroepDTO.sortOrder,
                budgetType = if (rekeningGroepDTO.budgetType !== null) enumValueOf<RekeningGroep.BudgetType>(
                    rekeningGroepDTO.budgetType
                ) else null,
            )
        )
        val rekeningen = rekeningGroepDTO.rekeningen.map { saveRekening(administratie, savedRekeningGroep, it) }

        if (betaalMiddelenRekeningGroepSoort.contains(rekeningGroep.rekeningGroepSoort)) {
            reserveringService.updateOpeningsReserveringsSaldo(administratie)
        }

        return rekeningGroep.fullCopy(rekeningen = rekeningen)
    }

    fun saveRekening(administratie: Administratie, rekeningGroep: RekeningGroep, rekeningDTO: RekeningDTO): Rekening {
        logger.debug("Opslaan rekening ${rekeningDTO.naam} voor ${administratie.naam} in groep ${rekeningGroep.naam}.")

        val betaalMethoden =
            rekeningDTO.betaalMethoden.mapNotNull {
                rekeningRepository.findRekeningAdministratieEnNaam(
                    administratie,
                    it.naam
                )
            }.filter { betaalMethodeRekeningGroepSoort.contains(it.rekeningGroep.rekeningGroepSoort) }
        if (betaalMethoden.size == 0 && !zonderBetaalMethodenRekeningGroepSoort.contains(rekeningGroep.rekeningGroepSoort))
            throw PM_RekeningMoetBetaalmethodeException(listOf(rekeningDTO.naam))

        val budgetPeriodiciteit =
            if (rekeningDTO.budgetPeriodiciteit != null)
                BudgetPeriodiciteit.valueOf(rekeningDTO.budgetPeriodiciteit.uppercase())
            else null

        if (vastBudgetType.contains(rekeningGroep.budgetType) && !geldigeBetaalDag(rekeningDTO.budgetBetaalDag))
            throw PM_GeenBetaaldagException(
                listOf(
                    rekeningDTO.naam,
                    rekeningGroep.budgetType?.name ?: "null",
                    administratie.naam
                )
            )

        val rekeningOpt = rekeningRepository.findRekeningAdministratieEnNaam(administratie, rekeningDTO.naam)
        val rekening = if (rekeningOpt != null) {
            logger.debug("Rekening bestaat al: ${rekeningOpt.naam} met id ${rekeningOpt.id} voor ${administratie.naam}")
            val aflossing = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.AFLOSSING) {
                if (rekeningOpt.aflossing == null) {
                    aflossingRepository.save(
                        Aflossing(
                            0,
                            LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.aflossing.schuldOpStartDatum),
                            rekeningDTO.aflossing.dossierNummer,
                            rekeningDTO.aflossing.notities
                        )
                    )
                } else {
                    aflossingRepository.save(
                        rekeningOpt.aflossing.fullCopy(
                            LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                            BigDecimal(rekeningDTO.aflossing.schuldOpStartDatum),
                            rekeningDTO.aflossing.dossierNummer,
                            rekeningDTO.aflossing.notities
                        )
                    )
                }
            } else null
            val spaarpot = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.SPAARPOT) {
                if (rekeningOpt.spaarpot == null) {
                    spaartegoedRepository.save(
                        Spaarpot(
                            0,
                            if (rekeningDTO.spaarpot?.doelDatum != null)
                                LocalDate.parse(
                                    rekeningDTO.spaarpot.doelDatum,
                                    DateTimeFormatter.ISO_LOCAL_DATE
                                ) else null,
                            if (rekeningDTO.spaarpot?.doelBedrag != null)
                                BigDecimal(rekeningDTO.spaarpot.doelBedrag) else null,
                            rekeningDTO.spaarpot?.notities ?: ""
                        )
                    )
                } else {
                    spaartegoedRepository.save(
                        rekeningOpt.spaarpot.fullCopy(
                            if (rekeningDTO.spaarpot?.doelDatum != null)
                                LocalDate.parse(
                                    rekeningDTO.spaarpot.doelDatum,
                                    DateTimeFormatter.ISO_LOCAL_DATE
                                ) else null,
                            if (rekeningDTO.spaarpot?.doelBedrag != null)
                                BigDecimal(rekeningDTO.spaarpot.doelBedrag) else null,
                            rekeningDTO.spaarpot?.notities ?: ""
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
                    budgetPeriodiciteit = budgetPeriodiciteit,
                    budgetBedrag = rekeningDTO.budgetBedrag,
                    budgetVariabiliteit = rekeningDTO.budgetVariabiliteit,
                    maanden = rekeningDTO.maanden,
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing ?: rekeningOpt.aflossing,
                    spaarpot = spaarpot ?: rekeningOpt.spaarpot,
                )
            )
        } else {

            val aflossing = if (rekeningGroep.rekeningGroepSoort == RekeningGroepSoort.AFLOSSING) {
                aflossingRepository.save(
                    Aflossing(
                        0,
                        LocalDate.parse(rekeningDTO.aflossing!!.startDatum, DateTimeFormatter.ISO_LOCAL_DATE),
                        BigDecimal(rekeningDTO.aflossing.schuldOpStartDatum),
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
                    budgetPeriodiciteit = budgetPeriodiciteit,
                    budgetBedrag = rekeningDTO.budgetBedrag,
                    budgetVariabiliteit = rekeningDTO.budgetVariabiliteit,
                    maanden = rekeningDTO.maanden,
                    betaalMethoden = betaalMethoden,
                    aflossing = aflossing,
                    spaarpot = null,
                )
            )

            savedRekening
        }
        val periode = periodeService.getLaatstGeslotenOfOpgeruimdePeriode(administratie)
        val saldoOpt = saldoRepository.findOneByPeriodeAndRekening(periode, rekening)
        if (saldoOpt == null) {
            saldoRepository.save(
                Saldo(
                    id = 0,
                    rekening = rekening,
                    openingsBalansSaldo = rekeningDTO.saldo ?: BigDecimal.ZERO,
                    openingsReserveSaldo = rekeningDTO.reserve ?: BigDecimal.ZERO,
                    periode = periode,
                    openingsAchterstand = BigDecimal.ZERO,
                    budgetMaandBedrag = BigDecimal.ZERO,
                    periodeBetaling = BigDecimal.ZERO
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
        logger.debug(
            "Opslaan rekening ${rekening.naam} voor ${administratie.naam} en periodiciteit ${rekening.budgetPeriodiciteit} met bedrag ${rekening.budgetBedrag} " +
                    "openingsBalansSaldo: ${rekeningDTO.saldo ?: BigDecimal.ZERO}, openingsReserveSaldo: ${rekeningDTO.reserve ?: BigDecimal.ZERO}."
        )
        return rekening
    }

    fun geldigeBetaalDag(dag: Int?): Boolean {
        return (dag != null && (dag in 1..31))
    }
}