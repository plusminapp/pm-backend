package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Betaling
import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.service.PagingService
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class BetalingDao {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var pagingService: PagingService

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    fun search(
        administratie: Administratie,
        sizeAsString: String,
        pageAsString: String,
        sort: String,
        queryString: String,
        statusString: String?,
        fromDateAsString: String,
        toDateAsString: String
    ): PagingService.ContentWrapper {
        val selectClause = "SELECT b FROM Betaling b "
        val countClause = "SELECT COUNT(*) FROM Betaling b "

        val administratieSelectBody = "b.administratie.id = ${administratie.id}"

        val queryTokens = queryString.trim().split(" ")
        val fields = listOf(
            "betalingsSoort",
            "omschrijving",
        )
        val querySelectBody = if (queryTokens[0].isNotBlank()) {
            (queryTokens.indices).joinToString(prefix = " (", separator = ") AND (", postfix = ") ") { index ->
                fields.joinToString(" OR ") { field -> "LOWER(b.$field) LIKE :q$index" }
            }
        } else null

        val statusTokens: List<String>? = statusString?.trim()?.split(",")
        val statusSelectBody = if (statusTokens != null && statusTokens[0].isNotBlank()) {
            statusTokens.joinToString(separator = " OR ") { " b.status = '$it'" }
        } else null

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var fromDate = if (fromDateAsString.isNotBlank()) {
            try {
                LocalDate.parse(fromDateAsString, formatter)
            } catch (ex: java.time.format.DateTimeParseException) {
                logger.error("fromDate: ${ex.message}")
                null
            }
        } else null
        var toDate = if (toDateAsString.isNotBlank()) {
            try {
                LocalDate.parse(toDateAsString, formatter)
            } catch (ex: java.time.format.DateTimeParseException) {
                logger.error("toDate: ${ex.message}")
                null
            }
        } else null
        if (fromDate != null && toDate != null && fromDate > toDate) fromDate = toDate.also { toDate = fromDate }
        val fromDateSelectBody = if (fromDate != null) " (b.boekingsdatum >= :qfd) "
        else null
        val toDateSelectBody = if (toDate != null) " (b.boekingsdatum <= :qtd) "
        else null

        val queryBodyList = listOf(
            administratieSelectBody,
            querySelectBody,
            statusSelectBody,
            fromDateSelectBody,
            toDateSelectBody
        ).mapNotNull { it }
        val queryBody = if (queryBodyList.isNotEmpty())
            queryBodyList.joinToString(prefix = " WHERE (", separator = ") AND (", postfix = ") ")
        else ""

        val sortSplitted = sort.trim().split(":")
        val sortField = if (Betaling.sortableFields.contains(sortSplitted[0])) sortSplitted[0] else "boekingsdatum"

        val querySelect = selectClause + queryBody + " ORDER BY b.$sortField " +
                if (sortSplitted.size >= 2 && sortSplitted[1] == "desc") "DESC" else ""
        val queryCount = countClause + queryBody

        val pageRequest =
            pagingService.toPageRequest(pageAsString, sizeAsString, sort, Betaling.sortableFields, "boekingsdatum")
        val qSelect = entityManager
            .createQuery(querySelect, Betaling::class.java)
            .setFirstResult(pageRequest.offset.toInt())
            .setMaxResults(pageRequest.pageSize)

        val qCount = entityManager
            .createQuery(queryCount, Long::class.javaObjectType)

        val parameterMap: Map<String, String> = queryTokens.mapIndexed { index, s -> "q$index" to s }.toMap()
        if (queryTokens[0].isNotBlank()) parameterMap.forEach {
            qSelect.setParameter(it.key, "%${it.value.lowercase()}%")
            qCount.setParameter(it.key, "%${it.value.lowercase()}%")
        }
        if (fromDate != null) {
            qSelect.setParameter("qfd", fromDate)
            qCount.setParameter("qfd", fromDate)
        }
        if (toDate != null) {
            qSelect.setParameter("qtd", toDate)
            qCount.setParameter("qtd", toDate)
        }

        val content = (qSelect.resultList as List<Betaling>).map { it.toDTO() }
        val count = (qCount.singleResult)

        val page = PageImpl(content, pageRequest, count)
        return PagingService.ContentWrapper(
            data = page as Page<out Any>,
            gebruikersId = administratie.id,
            gebruikersEmail = administratie.naam,
            gebruikersBijnaam = administratie.naam
        )
    }
}
