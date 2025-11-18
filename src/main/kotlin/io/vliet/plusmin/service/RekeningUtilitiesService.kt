package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RekeningUtilitiesService {
    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun rekeningGroepenPerBetalingsSoort(
        administratie: Administratie,
        periode: Periode
    ): List<RekeningGroep.RekeningGroepPerBetalingsSoort> {
        val rekeningGroepenMetGeldigeRekeningen = findRekeningGroepenMetGeldigeRekeningen(administratie, periode)
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
        administratie: Administratie,
        periode: Periode
    ): List<RekeningGroep> {
        return rekeningGroepRepository.findRekeningGroepenVoorAdministratie(administratie)
            .map { rekeningGroep ->
                rekeningGroep.fullCopy(
                    rekeningen = rekeningGroep.rekeningen
                        .filter { it.rekeningIsGeldigInPeriode(periode) }
                        .map {
                            it.fullCopy(
                                betaalMethoden = it.betaalMethoden.map {
                                    it.fullCopy(
                                        betaalMethoden = emptyList()
                                    )
                                }.sortedBy { it.sortOrder }
                            )
                        }
                )
            }
            .sortedBy { it.sortOrder }
            .filter { it.rekeningen.isNotEmpty() }
    }
}