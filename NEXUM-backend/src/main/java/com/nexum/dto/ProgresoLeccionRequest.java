package com.nexum.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProgresoLeccionRequest(

        @NotNull(message = "El usuarioId es obligatorio")
        UUID usuarioId,

        @NotNull(message = "El leccionId es obligatorio")
        UUID leccionId,

        Boolean completado,

        @jakarta.validation.constraints.Min(value = 0, message = "El tiempo no puede ser negativo") Integer ultimoSegundoVisto,

        @jakarta.validation.constraints.Min(value = 0, message = "El tiempo no puede ser negativo") Integer tiempoActivo
) {}
