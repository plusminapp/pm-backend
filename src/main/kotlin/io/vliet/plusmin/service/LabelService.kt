package io.vliet.plusmin.service

import io.vliet.plusmin.domain.Label
import io.vliet.plusmin.domain.PM_LabelBatchInvalidException
import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.LabelRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service



@Service
class LabelService {

    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var labelRepository: LabelRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun createLabel(
        administratieId: Long,
        namen: List<String>
    ): Boolean {
        val administratieOpt = administratieRepository.findById(administratieId)
        if (administratieOpt.isEmpty) {
            return true
        }
        val administratie = administratieOpt.get()
        var blankCount = 0
        var conflictCount = 0
        for (naam in namen) {
            if (naam.isBlank()) {
                blankCount++
                continue
            }
            val existing = labelRepository.findByAdministratieAndNaam(administratie, naam)
            if (existing != null) {
                conflictCount++
                continue
            }
            try {
                labelRepository.save(Label(0, naam, administratie))
            } catch (e: DataIntegrityViolationException) {
                logger.warn("Duplicate label save prevented for $naam", e)
                conflictCount++
            }
        }
        if (blankCount > 0 || conflictCount > 0) {
            throw PM_LabelBatchInvalidException(listOf(blankCount.toString(), conflictCount.toString()))
        }
        return false
    }
}