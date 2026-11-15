package com.nexum.repository;

import com.nexum.entity.Examen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamenRepository extends JpaRepository<Examen, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Examen e where e.id=:id")
    java.util.Optional<Examen> bloquearPorId(@org.springframework.data.repository.query.Param("id") UUID id);
    boolean existsByLeccion_IdAndPublicadoTrue(UUID leccionId);
    List<Examen> findByLeccion_Modulo_Curso_IdAndPublicadoTrue(UUID cursoId);

    List<Examen> findByLeccion_Id(UUID leccionId);

    boolean existsByLeccion_Id(UUID leccionId);

    boolean existsByLeccion_IdAndIdNot(UUID leccionId, UUID id);
}
