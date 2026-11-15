package com.nexum.service;

import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccesoCursoService {
    private final InscripcionRepository inscripciones;
    private final UsuarioRepository usuarios;
    private final LeccionRepository lecciones;
    private final AccesoService acceso;
    public boolean puedeLeer(Curso curso) {
        if (acceso.esAdministrador()) return true;
        if (curso == null || curso.getDeletedAt() != null || curso.getEstado() != EstadoCurso.PUBLICADO) return false;
        UUID id;
        try { id = acceso.obtenerIdUsuarioAutenticado(); } catch (AccessDeniedException ex) { return false; }
        Usuario u = usuarios.findById(id).orElse(null);
        if (u == null || u.getDeletedAt() != null || !Boolean.TRUE.equals(u.getEmailVerificado())) return false;
        return inscripciones.findByUsuario_IdAndCurso_Id(id, curso.getId()).stream().anyMatch(i -> {
            if (!Set.of("ACTIVA", "COMPLETADA").contains(i.getEstado())) return false;
            if (curso.getPrecio() == null || curso.getPrecio().signum() == 0) return true;
            Pago p = i.getPago();
            return p != null && p.getUsuario().getId().equals(id) && p.getCurso() != null &&
                    p.getCurso().getId().equals(curso.getId()) && p.getEstado() != null &&
                    Set.of("COMPLETADO", "APROBADO", "PAGADO").contains(p.getEstado().toUpperCase(java.util.Locale.ROOT));
        });
    }
    public void exigir(Curso curso) {
        if (!puedeLeer(curso)) throw new AccessDeniedException("Debes verificar tu correo e inscribirte en el curso para acceder al contenido");
    }
    public void exigirLeccion(UUID id) {
        Leccion l = lecciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lección no encontrada"));
        exigir(l.getModulo().getCurso());
    }
}
