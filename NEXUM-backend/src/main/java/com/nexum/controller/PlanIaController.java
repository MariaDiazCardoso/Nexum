package com.nexum.controller;

import com.nexum.repository.PlanIaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PlanIaController {
    private final PlanIaRepository repository;

    public record PlanResponse(UUID id, String nombre, BigDecimal precio, String moneda,
                               Integer creditosPorPeriodo, String periodo) {}

    @GetMapping("/api/planes-ia")
    public List<PlanResponse> listar() {
        return repository.findByActivoTrue().stream()
                .map(p -> new PlanResponse(p.getId(), p.getNombre(), p.getPrecio(), p.getMoneda(),
                        p.getCreditosPorPeriodo(), p.getPeriodo()))
                .sorted(Comparator.comparing(PlanResponse::precio))
                .toList();
    }
}
