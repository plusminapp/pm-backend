//package io.vliet.plusmin.repository
//
//import io.vliet.plusmin.domain.Budget
//import io.vliet.plusmin.domain.Gebruiker
//import io.vliet.plusmin.domain.Rekening
//import org.springframework.data.jpa.repository.JpaRepository
//import org.springframework.data.jpa.repository.Query
//import org.springframework.stereotype.Repository
//
//@Repository
//interface BudgetRepository : JpaRepository<Budget, Long> {
//
//    @Query(
//        value = "SELECT b FROM Budget b  " +
//                "WHERE b.rekening.rekeningGroep.gebruiker = :gebruiker"
//    )
//    fun findBudgettenByGebruiker(gebruiker: Gebruiker): List<Budget>
//
//    @Query(
//        value = "SELECT b FROM Budget b  " +
//                "WHERE b.rekening = :rekening " +
//                "AND b.budgetNaam = :budgetNaam"
//    )
//    fun findByRekeningEnBudgetNaam(rekening: Rekening, budgetNaam: String): Budget?
//
//
//}