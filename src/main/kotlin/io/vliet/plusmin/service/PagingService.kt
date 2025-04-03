package io.vliet.plusmin.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class PagingService {

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    val ascDescList = listOf("asc", "desc")

    fun toPageRequest(
        pageAsString: String,
        sizeAsString: String,
        sort: String,
        sortableFields: Set<String>,
        defaultSorting: String
    ): PageRequest {
        val page: Int = try {
            pageAsString.toInt()
        } catch (e: Exception) {
            logger.warn("Invalid page paramater $pageAsString; using 0"); 0
        }
        val size: Int = try {
            if (sizeAsString.toInt() < 0) Integer.MAX_VALUE else sizeAsString.toInt()
        } catch (e: Exception) {
            logger.warn("Invalid size paramater $sizeAsString; using 25"); 25
        }

        val sortArray = sort.trim().split(":")
        val sortProperty: String = if (sortableFields.contains(sortArray[0])) sortArray[0] else {
            logger.warn("Invalid sortProperty paramater ${sortArray[0]}; using $defaultSorting"); defaultSorting
        }
        val sortIndex = if (sortArray.size == 2 && ascDescList.contains(sortArray[1].lowercase()))
            sortArray[1]
        else {
            if (sortArray.size == 2) logger.warn("Invalid sortIndex parameter ${sortArray[1]}; using asc.")
            "asc"
        }
        val sortObject =
            if (sortIndex == "asc") Sort.by(sortProperty).ascending() else Sort.by(sortProperty).descending()

        return PageRequest.of(page, size, sortObject)
    }

    data class ContentWrapper(
        val data: Page<out Any>,
        val gebruikersId: Long = 0,
        val gebruikersEmail: String = "",
        val gebruikersBijnaam: String = "",
    )
}
