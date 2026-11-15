package com.nexum.service;

import com.nexum.dto.*;
import com.nexum.entity.RolUsuario;
import com.nexum.entity.Usuario;
import com.nexum.entity.UsuarioXp;
import com.nexum.exception.ConflictoException;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.repository.UsuarioRepository;
import com.nexum.repository.UsuarioXpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioXpRepository usuarioXpRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UsuarioResponse registrar(UsuarioRegistroRequest request) {
        String email = normalizar(request.email());

        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflictoException("Ya existe un usuario con el email: " + email);
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.nombre().trim())
                .apellido(request.apellido().trim())
                .genero(request.genero())
                .fechaNacimiento(request.fechaNacimiento())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(RolUsuario.USUARIO)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);

        UsuarioXp xp = UsuarioXp.builder()
                .usuario(guardado)
                .xpTotal(0)
                .nivel(1)
                .rachaDias(0)
                .build();
        usuarioXpRepository.save(xp);

        return mapearConXp(guardado);
    }

    @Override
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::mapearConXp)
                .toList();
    }

    @Override
    public UsuarioResponse obtenerPorId(UUID id) {
        return mapearConXp(buscarOLanzar(id));
    }

    @Override
    public UsuarioResponse actualizar(UUID id, UsuarioRequest request) {
        Usuario usuario = buscarOLanzar(id);
        String email = normalizar(request.email());

        if (usuarioRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictoException("El email ya está en uso por otro usuario: " + email);
        }

        usuario.setNombre(request.nombre().trim());
        usuario.setApellido(request.apellido().trim());
        if (!email.equals(usuario.getEmail())) usuario.setEmailVerificado(false);
        usuario.setEmail(email);
        usuario.setAvatarUrl(request.avatarUrl());

        return mapearConXp(usuarioRepository.save(usuario));
    }

    @Override
    public UsuarioResponse actualizarParcial(UUID id, UsuarioPatchRequest request) {
        Usuario usuario = buscarOLanzar(id);

        if (request.nombre() != null) {
            usuario.setNombre(request.nombre().trim());
        }
        if (request.apellido() != null) {
            usuario.setApellido(request.apellido().trim());
        }
        if (request.email() != null) {
            String email = normalizar(request.email());
            if (usuarioRepository.existsByEmailAndIdNot(email, id)) {
                throw new ConflictoException("El email ya está en uso por otro usuario: " + email);
            }
            if (!email.equals(usuario.getEmail())) usuario.setEmailVerificado(false);
        usuario.setEmail(email);
        }
        if (request.avatarUrl() != null) {
            usuario.setAvatarUrl(request.avatarUrl());
        }

        return mapearConXp(usuarioRepository.save(usuario));
    }

    @Override
    public UsuarioResponse actualizarRol(UUID id, UsuarioRolPatchRequest request) {
        Usuario usuario = buscarOLanzar(id);
        usuario.setRol(request.rol());
        return mapearConXp(usuarioRepository.save(usuario));
    }

    @Override
    public UsuarioResponse actualizarEmailVerificado(UUID id, UsuarioEmailVerificadoPatchRequest request) {
        Usuario usuario = buscarOLanzar(id);
        usuario.setEmailVerificado(request.emailVerificado());
        return mapearConXp(usuarioRepository.save(usuario));
    }

    @Override
    public void eliminar(UUID id) {
        Usuario usuario = buscarOLanzar(id);
        usuarioRepository.delete(usuario);
    }

    private UsuarioResponse mapearConXp(Usuario usuario) {
        int xp = usuarioXpRepository.findById(usuario.getId())
                .map(UsuarioXp::getXpTotal)
                .orElse(0);
        return UsuarioResponse.desde(usuario, xp);
    }

    private Usuario buscarOLanzar(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con id: " + id));
    }

    private static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
