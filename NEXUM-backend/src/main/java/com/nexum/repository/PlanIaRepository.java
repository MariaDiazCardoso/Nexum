package com.nexum.repository;

import com.nexum.entity.PlanIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanIaRepository extends JpaRepository<PlanIa, UUID> {
    List<PlanIa> findByActivoTrue();
    Optional<PlanIa> findByNombreIgnoreCase(String nombre);
}
