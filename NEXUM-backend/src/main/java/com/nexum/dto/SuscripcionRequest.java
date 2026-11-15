package com.nexum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Crea/actualiza una suscripción IA.
 * Preferir planId. Si no se envía, se intenta resolver por nombre de plan (compatibilidad).
 */
public record SuscripcionRequest(
        @NotNull UUID usuarioId,
        UUID planId,
        @Size(max = 50) String plan,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Boolean renovacionAutomatica,
        @NotBlank @Size(max = 20) @jakarta.validation.constraints.Pattern(regexp = "(?i)activa|cancelada|vencida|pendiente") String estado
) {}
