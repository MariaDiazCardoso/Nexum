package com.nexum.service;
import com.nexum.entity.*;
import com.nexum.security.UsuarioDetails;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class JwtPasswordRevocationTest {
    @Test void cambiarPasswordInvalidaTokenAnterior() {
        var service=new JwtService(Base64.getEncoder().encodeToString(new byte[48]),60);
        var user=Usuario.builder().id(UUID.randomUUID()).email("ana@nexum.invalid").rol(RolUsuario.USUARIO).passwordHash("hash-anterior").build();
        String token=service.generarToken(new UsuarioDetails(user));
        assertThat(service.esValido(token,new UsuarioDetails(user))).isTrue();
        user.setPasswordHash("hash-nuevo");
        assertThat(service.esValido(token,new UsuarioDetails(user))).isFalse();
        assertThat(service.esValido(service.generarToken(new UsuarioDetails(user)),new UsuarioDetails(user))).isTrue();
    }
}
