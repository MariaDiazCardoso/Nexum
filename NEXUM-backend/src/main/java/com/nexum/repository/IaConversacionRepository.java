package com.nexum.repository;

import com.nexum.entity.IaConversacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IaConversacionRepository extends JpaRepository<IaConversacion, UUID> {
    List<IaConversacion> findByUsuario_IdOrderByFechaCreacionDesc(UUID usuarioId);
}
