package com.nexum.dto;

import com.nexum.entity.RolUsuario;
import com.nexum.entity.Usuario;
import com.nexum.entity.Genero;
import java.time.LocalDate;

import java.time.LocalDateTime;
import java.util.UUID;


public record UsuarioResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        RolUsuario rol,
        Integer puntosXp,          
        String avatarUrl,
        LocalDateTime fechaRegistro,
        LocalDateTime ultimaConexion,
        Boolean emailVerificado,
        Genero genero,
        LocalDate fechaNacimiento
) {
    public static UsuarioResponse desde(Usuario u) {
        return desde(u, 0); 
    }

    public static UsuarioResponse desde(Usuario u, int puntosXp) {
        return new UsuarioResponse(
                u.getId(),
                u.getNombre(),
                u.getApellido(),
                u.getEmail(),
                u.getRol(),
                puntosXp,
                u.getAvatarUrl(),
                u.getFechaRegistro(),
                u.getUltimaConexion(),
                u.getEmailVerificado(),
                u.getGenero(),
                u.getFechaNacimiento()
        );
    }
}
