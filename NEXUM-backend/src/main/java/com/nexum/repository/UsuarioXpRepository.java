package com.nexum.repository;

import com.nexum.entity.UsuarioXp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsuarioXpRepository extends JpaRepository<UsuarioXp, UUID> {
}
