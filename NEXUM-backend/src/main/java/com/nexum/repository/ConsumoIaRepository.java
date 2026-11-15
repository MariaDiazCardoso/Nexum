package com.nexum.repository;

import com.nexum.entity.ConsumoIa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ConsumoIaRepository extends JpaRepository<ConsumoIa, UUID> {

    @Query("SELECT COALESCE(SUM(c.creditosUsados), 0) FROM ConsumoIa c WHERE c.suscripcion.id = :suscripcionId")
    int sumCreditosUsadosBySuscripcionId(@Param("suscripcionId") UUID suscripcionId);
}
