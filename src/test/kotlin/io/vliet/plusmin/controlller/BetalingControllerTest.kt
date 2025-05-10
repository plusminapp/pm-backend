package io.vliet.plusmin.controlller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.vliet.plusmin.controller.*
import io.vliet.plusmin.domain.Gebruiker
import io.vliet.plusmin.repository.BetalingRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false) // nodig om security te omzeilen
class BetalingControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var betalingRepository: BetalingRepository

    @MockkBean
    lateinit var gebruikerController: GebruikerController

    @MockkBean
    lateinit var betalingController: BetalingController

    @MockkBean
    lateinit var aflossingController: AflossingController

    @MockkBean
    lateinit var budgetController: BudgetController

    @MockkBean
    lateinit var demoController: DemoController

    @MockkBean
    lateinit var periodeController: PeriodeController

    @MockkBean
    lateinit var rekeningController: RekeningController

    @MockkBean
    lateinit var saldoController: SaldoController

    var dateNow: LocalDate = LocalDate.now()

    val testGebruiker = Gebruiker(id = 2, bijnaam = "testUser2", email = "testUser2@example.com")
    val testVrijwilliger= Gebruiker(id = 2, bijnaam = "testtestVrijwilliger2", email = "testVrijwilliger2@example.com")


    @Test
    fun `should successfully return laatste Betaling Datum Bij Gebruiker`() {
//        every { gebruikerController.checkAccess(1)} returns Pair (testGebruiker, testVrijwilliger)
        every { betalingRepository.findLaatsteBetalingDatumBijGebruiker(testGebruiker) } returns dateNow
        every { betalingController.getDatumLaatsteBetaling(2) } returns ResponseEntity.ok(dateNow)

        mockMvc.perform(get("/betalingen/hulpvrager/2/betalingvalidatie"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string("\"" + dateNow.toString() + "\""));
    }
}
