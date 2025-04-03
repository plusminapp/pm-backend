package io.vliet.plusmin.controller

import io.swagger.v3.oas.annotations.Operation
import io.vliet.plusmin.domain.Budget
import io.vliet.plusmin.repository.BudgetRepository
import io.vliet.plusmin.repository.RekeningRepository
import io.vliet.plusmin.service.BudgetService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/budget")
class BudgetController {

    @Autowired
    lateinit var budgetService: BudgetService

    @Autowired
    lateinit var budgetRepository: BudgetRepository

    @Autowired
    lateinit var rekeningRepository: RekeningRepository

    @Autowired
    lateinit var gebruikerController: GebruikerController

    val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Operation(summary = "GET de stand voor hulpvrager op datum")
    @GetMapping("/hulpvrager/{hulpvragerId}")
    fun getBudgettenVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
    ): List<Budget> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("GET BudgetController.getBudgettenVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return budgetRepository.findBudgettenByGebruiker(hulpvrager)
    }

    @Operation(summary = "PUT de saldi voor hulpvrager")
    @PutMapping("/hulpvrager/{hulpvragerId}")
    fun upsertBudgetVoorHulpvrager(
        @PathVariable("hulpvragerId") hulpvragerId: Long,
        @Valid @RequestBody budgetDTOListDTO: List<Budget.BudgetDTO>): ResponseEntity<Any> {
        val (hulpvrager, vrijwilliger) = gebruikerController.checkAccess(hulpvragerId)
        logger.info("PUT BudgetController.upsertBudgetVoorHulpvrager() voor ${hulpvrager.email} door ${vrijwilliger.email}")
        return ResponseEntity.ok().body(budgetService.saveAll(hulpvrager, budgetDTOListDTO))
    }


}

