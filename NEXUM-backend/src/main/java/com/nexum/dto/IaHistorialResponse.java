package com.nexum.dto;

import com.nexum.entity.IaConversacionContexto;
import com.nexum.entity.IaMensaje;

import java.time.LocalDateTime;
import java.util.UUID;

public record IaHistorialResponse(
        UUID id,
        UUID usuarioId,
        UUID conversacionId,
        UUID cursoId,
        UUID leccionId,
        String rol,
        String mensaje,
        LocalDateTime fechaCreacion
) {
    public static IaHistorialResponse desde(IaMensaje m, IaConversacionContexto ctx) {
        return new IaHistorialResponse(
                m.getId(),
                m.getConversacion() != null && m.getConversacion().getUsuario() != null
                        ? m.getConversacion().getUsuario().getId() : null,
                m.getConversacion() != null ? m.getConversacion().getId() : null,
                ctx != null && ctx.getCurso() != null ? ctx.getCurso().getId() : null,
                ctx != null && ctx.getLeccion() != null ? ctx.getLeccion().getId() : null,
                m.getRol(),
                m.getMensaje(),
                m.getFechaCreacion()
        );
    }

    public static IaHistorialResponse desde(IaMensaje m) {
        return desde(m, null);
    }
}
