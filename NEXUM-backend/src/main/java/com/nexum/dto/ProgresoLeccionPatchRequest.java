package com.nexum.dto;

public record ProgresoLeccionPatchRequest(
        Boolean completado,
        @jakarta.validation.constraints.Min(value = 0, message = "El tiempo no puede ser negativo") Integer ultimoSegundoVisto,
        @jakarta.validation.constraints.Min(value = 0, message = "El tiempo no puede ser negativo") Integer tiempoActivo
) {}
