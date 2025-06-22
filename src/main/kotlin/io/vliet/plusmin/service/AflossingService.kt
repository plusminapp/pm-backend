package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.domain.Rekening
import io.vliet.plusmin.domain.RekeningGroep
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class AflossingService {
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

    fun getAflossingenOpDatum(
        gebruiker: Gebruiker,
        peilDatum: LocalDate,
    ): List<Rekening.RekeningDTO> {
        val periode = periodeRepository.getPeriodeGebruikerEnDatum(
            gebruiker.id,
            peilDatum
        ) ?: return emptyList()
        val rekeningGroepen = rekeningGroepRepository.findRekeningGroepenOpSoort(
            gebruiker,
            RekeningGroep.RekeningGroepSoort.AFLOSSING
        )
        return rekeningGroepen
            .flatMap { it.rekeningen }
            .map { it.toDTO(periode) }
    }
}