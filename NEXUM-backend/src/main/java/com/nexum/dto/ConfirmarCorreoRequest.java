package com.nexum.dto;
import jakarta.validation.constraints.*;
public record ConfirmarCorreoRequest(@NotBlank @Email @Size(max=255) String email,
        @NotBlank @Pattern(regexp="[0-9]{6}", message="El código debe tener 6 dígitos") String codigo) {}
