package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.PM_LaatsteGeslotenPeriodeNotFoundException
import io.vliet.plusmin.domain.PM_NoPeriodException
import io.vliet.plusmin.domain.PM_PeriodeNotFoundException
import io.vliet.plusmin.domain.Periode
import io.vliet.plusmin.repository.PeriodeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class PeriodeService {
    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getPeriode(administratie: Administratie, datum: LocalDate): Periode {
        return periodeRepository.getPeriodeAdministratieEnDatum(administratie.id, datum)
            ?: throw PM_NoPeriodException(listOf(administratie.naam, datum.format(DateTimeFormatter.ISO_LOCAL_DATE)))
    }

    fun getLaatstGeslotenOfOpgeruimdePeriode(administratie: Administratie): Periode {
        return periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(administratie)
            ?: throw PM_LaatsteGeslotenPeriodeNotFoundException(listOf(administratie.naam))
    }

    fun getPeriode(periodeId: Long, administratie: Administratie): Periode {

        val periode = periodeRepository.getPeriodeById(periodeId)
        if (periode == null || periode.administratie.id != administratie.id) {
            throw PM_PeriodeNotFoundException(listOf(periodeId.toString()))
        }
        return periode
    }

    fun berekenPeriodeDatums(periodeWisselDag: Int, datum: LocalDate): Pair<LocalDate, LocalDate> {
        val jaar = datum.year
        val maand = datum.monthValue
        val dagInMaand = datum.dayOfMonth

        val startDatum: LocalDate = if (dagInMaand >= periodeWisselDag) {
            LocalDate.of(jaar, maand, periodeWisselDag)
        } else {
            LocalDate.of(jaar, maand, periodeWisselDag).minusMonths(1)
        }
        return Pair(startDatum, startDatum.plusMonths(1).minusDays(1))
    }

    /*
        check of de huidige periode bestaat, anders aanmaken sinds de laatst bestaande periode
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun checkPeriodesVoorGebruiker(administratie: Administratie) {
        val laatstePeriode = periodeRepository.getLaatstePeriodeVoorAdministratie(administratie.id)
        logger.debug(
            "laatstePeriode voor {}: {} -> {} {}",
            administratie.naam,
            laatstePeriode?.periodeStartDatum,
            laatstePeriode?.periodeEindDatum,
            laatstePeriode?.periodeStatus
        )
        val periodes = periodeRepository.getPeriodesVoorAdministrtatie(administratie)
        logger.debug("periodes voor ${administratie.naam}: ${periodes.map { it.periodeStartDatum }.joinToString(", ")} ")
        if (laatstePeriode == null) {
            logger.warn("voor ${administratie.naam}: laatstePeriode == null")
            creeerInitielePeriode(administratie, berekenPeriodeDatums(administratie.periodeDag, LocalDate.now()).first)
        } else if (laatstePeriode.periodeEindDatum < LocalDate.now()) {
            logger.info("creeerVolgendePeriodes voor ${administratie.naam}: ${laatstePeriode.periodeStartDatum}->${laatstePeriode.periodeEindDatum}")
            creeerVolgendePeriodes(laatstePeriode)
        }
    }

    fun creeerVolgendePeriodes(vorigePeriode: Periode) {
        if (vorigePeriode.periodeStatus == Periode.PeriodeStatus.HUIDIG) {
            periodeRepository.save(vorigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.OPEN))
        }
        val nieuwePeriode = periodeRepository.save(
            Periode(
                administratie = vorigePeriode.administratie,
                periodeStartDatum = vorigePeriode.periodeEindDatum.plusDays(1),
                periodeEindDatum = vorigePeriode.periodeEindDatum.plusMonths(1),
                periodeStatus = Periode.PeriodeStatus.HUIDIG
            )
        )
        if (nieuwePeriode.periodeEindDatum < LocalDate.now()) {
            creeerVolgendePeriodes(nieuwePeriode)
        }
    }

    fun creeerInitielePeriode(administratie: Administratie, startDatum: LocalDate) {
        if (periodeRepository.getPeriodesVoorAdministrtatie(administratie).size == 0) {
            val periodeStartDatum = berekenPeriodeDatums(administratie.periodeDag, startDatum).first
            logger.info("Initiële periode gecreëerd voor ${administratie.naam} op ${periodeStartDatum}")
            val initielePeriode = periodeRepository.save(
                Periode(
                    0,
                    administratie,
                    periodeStartDatum.minusDays(1),
                    periodeStartDatum.minusDays(1),
                    Periode.PeriodeStatus.OPGERUIMD
                )
            )
            if (initielePeriode.periodeEindDatum.isBefore(LocalDate.now())) {
                creeerVolgendePeriodes(initielePeriode)
            }
        }
    }

    fun pasPeriodeDagAan(administratie: Administratie, administratieDTO: Administratie.AdministratieDTO) {
        val periodes = periodeRepository.getPeriodesVoorAdministrtatie(administratie).sortedBy { it.periodeStartDatum }
        if (periodes.size == 2 &&
            periodes[0].periodeStartDatum.equals(periodes[0].periodeEindDatum) &&
            periodes[1].periodeStatus.equals(Periode.PeriodeStatus.HUIDIG)
        ) { // initiële periode + huidige periode
            val initielePeriodeEindDatum =
                berekenPeriodeDatums(administratieDTO.periodeDag, LocalDate.now()).first.minusDays(1)
            logger.info("Initiele PeriodeDag aanpassen voor ${administratie.naam} van ${periodes[0].periodeStartDatum} naar ${initielePeriodeEindDatum}")
            periodeRepository.save(
                periodes[0].fullCopy(
                    periodeStartDatum = initielePeriodeEindDatum,
                    periodeEindDatum = initielePeriodeEindDatum
                )
            )
            periodeRepository.save(
                periodes[1].fullCopy(
                    periodeStartDatum = initielePeriodeEindDatum.plusDays(1),
                    periodeEindDatum = initielePeriodeEindDatum.plusMonths(1)
                )
            )
        } else {
            val teVerschuivenPeriodes =
                periodes.filter { it.periodeStatus == Periode.PeriodeStatus.OPEN || it.periodeStatus == Periode.PeriodeStatus.HUIDIG }
                    .sortedBy { it.periodeStartDatum }
            logger.debug(
                "PeriodeDag aanpassen voor ${administratie.naam}: teVerschuivenPeriodes: " +
                        teVerschuivenPeriodes.joinToString(", ") { "${it.periodeStartDatum} (${it.periodeStatus})" }
            )
            val periodeEindDatum1stePeriode =
                berekenPeriodeDatums(administratieDTO.periodeDag, teVerschuivenPeriodes[0].periodeEindDatum).second
            logger.debug("periodeEindDatum1stePeriode: {}", periodeEindDatum1stePeriode)
            logger.debug(
                "Periode verschuiven van {}/{} -> {}/{}",
                teVerschuivenPeriodes[0].periodeStartDatum,
                teVerschuivenPeriodes[0].periodeEindDatum,
                teVerschuivenPeriodes[0].periodeStartDatum,
                periodeEindDatum1stePeriode
            )
            periodeRepository.save(
                teVerschuivenPeriodes[0].fullCopy(
                    periodeStartDatum = teVerschuivenPeriodes[0].periodeStartDatum,
                    periodeEindDatum = periodeEindDatum1stePeriode
                )
            )
            teVerschuivenPeriodes.drop(1).forEach {
                val (periodeStartDatum, periodeEindDatum) = berekenPeriodeDatums(
                    administratieDTO.periodeDag,
                    it.periodeEindDatum
                )
                logger.debug(
                    "Periode verschuiven van {}/{} -> {}/{}",
                    it.periodeStartDatum,
                    it.periodeEindDatum,
                    periodeStartDatum,
                    periodeEindDatum
                )
                periodeRepository.save(
                    it.fullCopy(
                        periodeStartDatum = periodeStartDatum,
                        periodeEindDatum = periodeEindDatum
                    )
                )
            }
        }
    }
}