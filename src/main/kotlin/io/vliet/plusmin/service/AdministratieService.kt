package io.vliet.plusmin.service

import io.vliet.plusmin.repository.AdministratieRepository
import io.vliet.plusmin.repository.RekeningGroepRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AdministratieService {
    @Autowired
    lateinit var administratieRepository: AdministratieRepository

    @Autowired
    lateinit var periodeService: PeriodeService

    @Autowired
    lateinit var rekeningGroepRepository: RekeningGroepRepository

    @Autowired
    lateinit var rekeningService: RekeningService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)


}