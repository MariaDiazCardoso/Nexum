package com.nexum.repository;

import com.nexum.entity.SuscripcionIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SuscripcionIaRepository extends JpaRepository<SuscripcionIa, UUID> {
    List<SuscripcionIa> findByUsuario_Id(UUID usuarioId);
    List<SuscripcionIa> findByUsuario_IdAndEstado(UUID usuarioId, String estado);
}
