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
class RekeningUtilitiesService {
    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

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