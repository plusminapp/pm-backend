package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.PeriodeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PeriodeService {
    @Autowired
    lateinit var periodeRepository: PeriodeRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun getPeriode(gebruiker: Gebruiker, datum: LocalDate): Periode {
        return periodeRepository.getPeriodeGebruikerEnDatum(gebruiker.id, datum)
            ?: throw IllegalStateException("Geen periode voor ${gebruiker.email} op ${datum}")
    }

    fun getLaatstGeslotenOfOpgeruimdePeriode(gebruiker: Gebruiker): Periode {
        return periodeRepository.getLaatstGeslotenOfOpgeruimdePeriode(gebruiker)
            ?: throw IllegalStateException("Geen initiële periode voor gebruiker ${gebruiker.email}")
    }

    fun berekenPeriodeDatums(dag: Int, datum: LocalDate): Pair<LocalDate, LocalDate> {
        val jaar = datum.year
        val maand = datum.monthValue
        val dagInMaand = datum.dayOfMonth

        val startDatum: LocalDate = if (dagInMaand >= dag) {
            LocalDate.of(jaar, maand, dag)
        } else {
            LocalDate.of(jaar, maand, dag).minusMonths(1)
        }
        return Pair(startDatum, startDatum.plusMonths(1).minusDays(1))
    }

    /*
        check of de huidige periode bestaat, anders aanmaken sinds de laatst bestaande periode
     */
    fun checkPeriodesVoorGebruiker(gebruiker: Gebruiker) {
        val laatstePeriode = periodeRepository.getLaatstePeriodeVoorGebruiker(gebruiker.id)
        logger.debug("laatstePeriode voor ${gebruiker.email}: ${laatstePeriode?.periodeStartDatum} -> ${laatstePeriode?.periodeEindDatum} ${laatstePeriode?.periodeStatus}")
        val periodes = periodeRepository.getPeriodesVoorGebruiker(gebruiker)
        logger.debug("periodes voor ${gebruiker.email}: ${periodes.map { it.periodeStartDatum }.joinToString(", ")} ")
        if (laatstePeriode == null) {
            logger.warn("voor ${gebruiker.email}: laatstePeriode == null")
            creeerInitielePeriode(gebruiker, berekenPeriodeDatums(gebruiker.periodeDag, LocalDate.now()).first)
        } else if (laatstePeriode.periodeEindDatum < LocalDate.now()) {
            logger.info("creeerVolgendePeriodes voor ${gebruiker.email}: ${laatstePeriode.periodeStartDatum}->${laatstePeriode.periodeEindDatum}")
            creeerVolgendePeriodes(laatstePeriode)
        }
    }

    fun creeerVolgendePeriodes(vorigePeriode: Periode) {
        if (vorigePeriode.periodeStatus == Periode.PeriodeStatus.HUIDIG) {
            periodeRepository.save(vorigePeriode.fullCopy(periodeStatus = Periode.PeriodeStatus.OPEN))
        }
        val nieuwePeriode = periodeRepository.save(
            Periode(
                gebruiker = vorigePeriode.gebruiker,
                periodeStartDatum = vorigePeriode.periodeEindDatum.plusDays(1),
                periodeEindDatum = vorigePeriode.periodeEindDatum.plusMonths(1),
                periodeStatus = Periode.PeriodeStatus.HUIDIG
            )
        )
        if (nieuwePeriode.periodeEindDatum < LocalDate.now()) {
            creeerVolgendePeriodes(nieuwePeriode)
        }
    }

    fun creeerInitielePeriode(gebruiker: Gebruiker, startDatum: LocalDate) {
        if (periodeRepository.getPeriodesVoorGebruiker(gebruiker).size == 0) {
            val periodeStartDatum = berekenPeriodeDatums(gebruiker.periodeDag, startDatum).first
            logger.info("Initiële periode gecreëerd voor ${gebruiker.email} op ${periodeStartDatum}")
            val initielePeriode = periodeRepository.save(
                Periode(
                    0,
                    gebruiker,
                    periodeStartDatum.minusDays(1),
                    periodeStartDatum.minusDays(1),
                    Periode.PeriodeStatus.GESLOTEN
                )
            )
            if (initielePeriode.periodeEindDatum < LocalDate.now()) {
                creeerVolgendePeriodes(initielePeriode)
            }
        }
    }

    fun pasPeriodeDagAan(gebruiker: Gebruiker, gebruikerDTO: Gebruiker.GebruikerDTO) {
        val periodes = periodeRepository.getPeriodesVoorGebruiker(gebruiker).sortedBy { it.periodeStartDatum }
        if (periodes.size == 2 &&
            periodes[0].periodeStartDatum == periodes[0].periodeEindDatum &&
            periodes[1].periodeStatus == Periode.PeriodeStatus.HUIDIG
        ) { // initiële periode + huidige periode
            val initielePeriodeEindDatum =
                berekenPeriodeDatums(gebruikerDTO.periodeDag, LocalDate.now()).first.minusDays(1)
            logger.info("Initiele PeriodeDag aanpassen voor ${gebruiker.email} van ${periodes[0].periodeStartDatum} naar ${initielePeriodeEindDatum}")
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
                "PeriodeDag aanpassen voor ${gebruiker.email}: teVerschuivenPeriodes: " +
                        teVerschuivenPeriodes.map { "${it.periodeStartDatum} (${it.periodeStatus})" }.joinToString(", ")
            )
            val periodeEindDatum1stePeriode =
                berekenPeriodeDatums(gebruikerDTO.periodeDag, teVerschuivenPeriodes[0].periodeEindDatum).second
            logger.debug("periodeEindDatum1stePeriode: {}", periodeEindDatum1stePeriode)
            logger.debug("Periode verschuiven van ${teVerschuivenPeriodes[0].periodeStartDatum}/${teVerschuivenPeriodes[0].periodeEindDatum} -> ${teVerschuivenPeriodes[0].periodeStartDatum}/$periodeEindDatum1stePeriode")
            periodeRepository.save(
                teVerschuivenPeriodes[0].fullCopy(
                    periodeStartDatum = teVerschuivenPeriodes[0].periodeStartDatum,
                    periodeEindDatum = periodeEindDatum1stePeriode
                )
            )
            teVerschuivenPeriodes.drop(1).forEach {
                val (periodeStartDatum, periodeEindDatum) = berekenPeriodeDatums(
                    gebruikerDTO.periodeDag,
                    it.periodeEindDatum
                )
                logger.debug("Periode verschuiven van ${it.periodeStartDatum}/${it.periodeEindDatum} -> $periodeStartDatum/$periodeEindDatum")
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