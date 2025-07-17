package io.vliet.plusmin.domain

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CashFlow(
    val datum: LocalDate,
    val inkomsten: BigDecimal,
    val uitgaven: BigDecimal,
    val saldo: BigDecimal? = null,
    val prognose: BigDecimal? = null,
) {
}