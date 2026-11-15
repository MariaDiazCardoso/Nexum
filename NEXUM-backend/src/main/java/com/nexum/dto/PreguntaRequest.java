package com.nexum.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PreguntaRequest(

        @NotNull(message = "El examenId es obligatorio")
        UUID examenId,

        @NotBlank(message = "El texto de la pregunta es obligatorio")
        @jakarta.validation.constraints.Size(max=5000) String textoPregunta,

        @jakarta.validation.constraints.Size(max=5000) String explicacionRespuesta,

        @NotNull(message = "La lista de opciones es obligatoria")
        @jakarta.validation.constraints.Size(min=2,max=10) List<@jakarta.validation.constraints.NotNull @Valid OpcionRespuestaRequest> opciones
) {}
