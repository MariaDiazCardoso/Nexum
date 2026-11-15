package com.nexum.dto;

import jakarta.validation.constraints.*;

/**
 * DTO para actualización parcial de un usuario (PATCH /api/usuarios/{id}).
 * Todos los campos son opcionales; el servicio solo actualiza los no nulos.
 */
public record UsuarioPatchRequest(

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El apellido no puede estar vacío")
        String apellido,

        @Email(message = "El email no tiene un formato válido")
        @Size(max = 255, message = "El email no puede superar los 255 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El correo no puede estar vacío")
        String email,

        @Size(max = 255, message = "La URL del avatar no puede superar los 255 caracteres")
        String avatarUrl
) {}

