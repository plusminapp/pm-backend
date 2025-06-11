package io.vliet.plusmin.service

import io.vliet.plusmin.domain.*
import io.vliet.plusmin.repository.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class AflossingGrafiekService {
//    @Autowired
//    lateinit var aflossingRepository: AflossingRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

//    fun genereerAflossingGrafiekData(gebruiker: Gebruiker): String {
//        val aflossingen = aflossingRepository.findAflossingenVoorGebruiker(gebruiker)
//        val aflossingGrafiekDataLijst: List<AflossingGrafiekData> =
//            aflossingen.flatMap { aflossing ->
//                genereerAflossingSaldoDTO(aflossing)
//            }
//        val aflossingGrafiekDataMap: Map<String, List<Saldo.SaldoDTO>> = aflossingGrafiekDataLijst
//            .groupBy { it.maand }
//            .mapValues { entry -> entry.value.map { it.aflossingSaldoDTO } }
//        return toGrafiekDataJsonString(aflossingGrafiekDataMap)
//    }

    fun toGrafiekDataJsonString(aflossingGrafiekDataMap: Map<String, List<Saldo.SaldoDTO>>): String {
        return buildString {
            append("[\n")
            aflossingGrafiekDataMap.forEach { (maand, saldoDtoLijst) ->
                append("{ month: '${maand}'")
                saldoDtoLijst.forEach { saldoDto ->
                    append(", ${saldoDto.rekeningNaam.lowercase().replace("\\s".toRegex(), "")}: ${saldoDto.saldo}")
                }
                append(" },\n")
            }
            append("\n];")
        }
    }

//    fun genereerAflossingSaldoDTO(aflossing: Aflossing): List<AflossingGrafiekData> {
//        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
//        val aflossingGrafiekDataLijst = mutableListOf<AflossingGrafiekData>()
//        var huidigeMaand = aflossing.startDatum.withDayOfMonth(1)
//        var huidigeBedrag = aflossing.eindBedrag
//        while (huidigeBedrag > BigDecimal(0)) {
//            aflossingGrafiekDataLijst.add(
//                AflossingGrafiekData(
//                    maand = huidigeMaand.format(formatter),
//                    aflossingSaldoDTO = Saldo.SaldoDTO(
//                        rekeningNaam = aflossing.rekening.naam,
//                        saldo = huidigeBedrag,
//                    )
//                )
//            )
//            huidigeBedrag -= aflossing.rekening.budgetBedrag ?: BigDecimal.ZERO
//            huidigeMaand = huidigeMaand.plus(1, ChronoUnit.MONTHS)
//        }
//        return aflossingGrafiekDataLijst.toList()
//    }
//
//    fun genereerAflossingGrafiekSeries(gebruiker: Gebruiker): String {
//        val rekeningen = aflossingRepository
//            .findAflossingenVoorGebruiker(gebruiker)
//            .map { it.rekening }
//
//        return toGrafiekSerieJsonString(rekeningen.map {
//            AflossingGrafiekSerie(
//                yKey = it.naam.lowercase().replace("\\s".toRegex(), ""),
//                yName = it.naam
//            )
//        })
//    }

    fun toGrafiekSerieJsonString(aflossingGrafiekSerieLijst: List<AflossingGrafiekSerie>): String {
        return buildString {
            append("[\n")
            aflossingGrafiekSerieLijst.forEach { aflossingGrafiekSerie ->
                append(
                    """          {
            type: "area",
            xKey: "month",
            yKey: "${aflossingGrafiekSerie.yKey}",
            yName: "${aflossingGrafiekSerie.yName}",
            stacked: true,
          },
"""
                )
            }
            append("\n];")
        }
    }

    data class AflossingGrafiekDTO(
        val aflossingGrafiekSerie: String,
        val aflossingGrafiekData: String
    )

    data class AflossingGrafiekSerie(
        val type: String = "area",
        val xKey: String = "month",
        val yKey: String,
        val yName: String,
        val stacked: Boolean = true,
    )

    data class AflossingGrafiekData(
        val maand: String,
        val aflossingSaldoDTO: Saldo.SaldoDTO
    )
}