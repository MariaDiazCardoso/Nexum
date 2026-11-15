package com.nexum.repository;

import com.nexum.entity.IaMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IaMensajeRepository extends JpaRepository<IaMensaje, UUID> {

    List<IaMensaje> findByConversacion_IdOrderByFechaCreacionAsc(UUID conversacionId);

    @Query("""
        SELECT m FROM IaMensaje m
        JOIN m.conversacion c
        LEFT JOIN IaConversacionContexto ctx ON ctx.conversacion = c
        WHERE (:usuarioId IS NULL OR c.usuario.id = :usuarioId)
          AND (:cursoId IS NULL OR ctx.curso.id = :cursoId)
          AND (:leccionId IS NULL OR ctx.leccion.id = :leccionId)
        ORDER BY m.fechaCreacion ASC
        """)
    List<IaMensaje> buscarConFiltros(
            @Param("usuarioId") UUID usuarioId,
            @Param("cursoId") UUID cursoId,
            @Param("leccionId") UUID leccionId);
}
