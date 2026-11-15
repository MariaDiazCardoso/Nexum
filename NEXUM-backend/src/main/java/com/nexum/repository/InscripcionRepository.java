package com.nexum.repository;

import com.nexum.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscripcionRepository extends JpaRepository<Inscripcion, UUID> {

    List<Inscripcion> findByUsuario_Id(UUID usuarioId);

    List<Inscripcion> findByCurso_Id(UUID cursoId);

    List<Inscripcion> findByUsuario_IdAndCurso_Id(UUID usuarioId, UUID cursoId);

    @Query("SELECT i FROM Inscripcion i JOIN i.usuario u WHERE u.email = ?1")
    List<Inscripcion> buscarPorEmail(String email);

    Optional<Inscripcion> findByUsuario_IdAndCurso_IdAndEstado(UUID usuarioId, UUID cursoId, String estado);

    boolean existsByUsuario_IdAndCurso_Id(UUID usuarioId, UUID cursoId);

    boolean existsByCurso_Id(UUID cursoId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inscripcion i where i.usuario.id=?1 and i.curso.id=?2 and i.estado in ('ACTIVA','COMPLETADA')")
    Optional<Inscripcion> bloquearInscripcion(UUID usuarioId, UUID cursoId);
}
