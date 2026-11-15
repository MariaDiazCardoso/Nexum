package com.nexum.dto;

import com.nexum.entity.SuscripcionIa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SuscripcionResponse(
        UUID id,
        UUID usuarioId,
        UUID planId,
        String plan,
        BigDecimal precio,
        Integer creditosIa,
        Integer creditosRestantes,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Boolean renovacionAutomatica,
        String estado,
        LocalDateTime createdAt
) {
    public static SuscripcionResponse desde(SuscripcionIa s, int creditosUsados) {
        int asignados = s.getCreditosAsignados() == null ? 0 : s.getCreditosAsignados();
        int restantes = Math.max(0, asignados - creditosUsados);
        return new SuscripcionResponse(
                s.getId(),
                s.getUsuario() != null ? s.getUsuario().getId() : null,
                s.getPlan() != null ? s.getPlan().getId() : null,
                s.getPlan() != null ? s.getPlan().getNombre() : null,
                s.getPrecioCobrado(),
                asignados,
                restantes,
                s.getFechaInicio(),
                s.getFechaFin(),
                s.getRenovacionAutomatica(),
                s.getEstado(),
                s.getCreatedAt()
        );
    }

    public static SuscripcionResponse desde(SuscripcionIa s) {
        return desde(s, 0);
    }
}
