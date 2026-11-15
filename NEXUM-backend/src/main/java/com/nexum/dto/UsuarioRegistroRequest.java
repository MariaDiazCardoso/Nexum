package com.nexum.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexum.entity.Genero;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;

/**
 * DTO de registro de nuevo usuario (POST /api/usuarios).
 * El rol siempre se fuerza a USUARIO en el servicio (T03).
 */
public record UsuarioRegistroRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String apellido,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 255, message = "El email no puede superar los 255 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(
                regexp = "(?s)^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}\\s]).*$",
                message = "La contraseña debe incluir una mayúscula, una minúscula, un número y un carácter especial")
        String password,

        @NotNull(message = "El género es obligatorio")
        Genero genero,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
        LocalDate fechaNacimiento
) {
    @JsonIgnore
    @AssertTrue(message = "La contraseña supera el límite de 72 bytes; utiliza menos caracteres")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}

