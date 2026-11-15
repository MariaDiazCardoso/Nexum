package com.nexum.dto;

import jakarta.validation.constraints.NotBlank;

public record CodigoVerificacionValidacionRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}", message = "El código debe tener 6 dígitos") String codigo
) {}
