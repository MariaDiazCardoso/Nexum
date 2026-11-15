package com.nexum.repository;
import com.nexum.entity.RespuestaIntento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RespuestaIntentoRepository extends JpaRepository<RespuestaIntento,UUID> {
 List<RespuestaIntento> findByIntento_Id(UUID intentoId);
}
