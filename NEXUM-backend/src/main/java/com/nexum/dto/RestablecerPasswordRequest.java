package com.nexum.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.StandardCharsets;
public record RestablecerPasswordRequest(
    @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}\\.[A-Za-z0-9_-]{43}", message="El enlace de recuperación no es válido") String token,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(
                regexp = "(?s)^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}\\s]).*$",
                message = "La contraseña debe incluir una mayúscula, una minúscula, un número y un carácter especial")
        String password
) {
    @JsonIgnore
    @AssertTrue(message="La contraseña supera el límite de 72 bytes; utiliza menos caracteres")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
