package com.nexum.dto;
import jakarta.validation.constraints.*;
public record CorreoVerificacionRequest(@NotBlank @Email @Size(max=255) String email) {}
