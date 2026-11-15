package com.nexum.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public record EnviarEvaluacionRequest(@NotNull @Size(min=1,max=200) List<@NotNull @Valid Respuesta> respuestas) {
 public record Respuesta(@NotNull UUID preguntaId,@NotNull UUID opcionId) {}
}
