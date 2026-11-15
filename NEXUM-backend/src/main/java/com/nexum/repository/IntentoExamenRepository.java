package com.nexum.repository;
import com.nexum.entity.IntentoExamen;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface IntentoExamenRepository extends JpaRepository<IntentoExamen,UUID> {
 List<IntentoExamen> findByExamen_IdAndUsuario_IdOrderByNumeroIntentoAsc(UUID examenId,UUID usuarioId);
 boolean existsByExamen_Id(UUID examenId);
 boolean existsByExamen_Leccion_IdAndUsuario_IdAndAprobadoTrue(UUID leccionId,UUID usuarioId);
}
