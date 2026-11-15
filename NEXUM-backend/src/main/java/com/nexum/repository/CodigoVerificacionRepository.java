package com.nexum.repository;

import com.nexum.entity.CodigoVerificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CodigoVerificacion c where c.id=:id")
    java.util.Optional<CodigoVerificacion> bloquearPorId(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<CodigoVerificacion> findFirstByUsuario_IdAndTipoAndUsadoFalseOrderByFechaCreacionDesc(UUID usuarioId, String tipo);

    List<CodigoVerificacion> findByUsuario_Id(UUID usuarioId);

    @org.springframework.data.jpa.repository.Query("select c.usuario.id from CodigoVerificacion c where c.id=:id")
    java.util.Optional<UUID> propietario(@org.springframework.data.repository.query.Param("id") UUID id);
}
