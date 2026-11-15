package com.nexum.repository;

import com.nexum.entity.IaConversacionContexto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IaConversacionContextoRepository extends JpaRepository<IaConversacionContexto, UUID> {
    List<IaConversacionContexto> findByConversacion_Id(UUID conversacionId);
    Optional<IaConversacionContexto> findFirstByConversacion_Id(UUID conversacionId);
}
