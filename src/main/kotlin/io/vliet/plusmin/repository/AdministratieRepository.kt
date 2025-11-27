package io.vliet.plusmin.repository

import io.vliet.plusmin.domain.Administratie
import io.vliet.plusmin.domain.Gebruiker

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
interface AdministratieRepository : JpaRepository<Administratie, Long> {

    @Query(
        "select distinct a from Gebruiker g " +
                "join g.administraties a " +
                "where g = :gebruiker " +
                "and a.naam = :administratieNaam"
    )
    fun findAdministratieOpNaamEnGebruiker(administratieNaam: String, gebruiker: Gebruiker): Administratie?

    @Query("select distinct g from Gebruiker g join g.administraties a where a = :administratie")
    fun findGebruikersMetToegangTotAdministratie(administratie: Administratie): List<Gebruiker>


    @Query("select a from Administratie a where a.id = :administratieId")
    fun getVandaag(administratieId: Long): Administratie?

    @Modifying
    @Transactional
    @Query("UPDATE Administratie a SET a.vandaag = :vandaag WHERE a.id = :id")
    fun putVandaag(id: Long, vandaag: LocalDate?): Int

    @Modifying
    @Transactional
    @Query(
        "  DELETE FROM public.betaling WHERE administratie_id = :id;" +
                "  DELETE FROM public.saldo WHERE periode_id IN (" +
                "    SELECT id FROM public.periode WHERE administratie_id = :id" +
                "  );" +
                "  DELETE FROM public.saldo WHERE rekening_id IN (" +
                "    SELECT r.id FROM public.rekening r" +
                "    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id" +
                "    WHERE rg.administratie_id = :id" +
                "  );" +
                "  DELETE FROM public.rekening_betaal_methoden WHERE rekening_id IN (" +
                "    SELECT r.id FROM public.rekening r" +
                "    JOIN public.rekening_groep rg ON r.rekening_groep_id = rg.id" +
                "    WHERE rg.administratie_id = :id" +
                "  );" +
                "  DELETE FROM public.rekening WHERE rekening_groep_id IN (" +
                "    SELECT id FROM public.rekening_groep WHERE administratie_id = :id" +
                "  );" +
                "  DELETE FROM public.rekening_groep WHERE administratie_id = :id;" +
                "  DELETE FROM public.gebruiker_administratie WHERE administratie_id = :id;" +
                "  DELETE FROM public.periode WHERE administratie_id = :id;" +
                "  DELETE FROM public.administratie WHERE id = :id;",
        nativeQuery = true
    )
    fun deleteAdministratie(id: Long): Int
}

