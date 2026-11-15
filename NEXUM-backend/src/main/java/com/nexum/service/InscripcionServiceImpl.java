package com.nexum.service;

import com.nexum.dto.InscripcionPatchRequest;
import com.nexum.dto.InscripcionRequest;
import com.nexum.dto.InscripcionResponse;
import com.nexum.entity.Curso;
import com.nexum.entity.EstadoCurso;
import com.nexum.entity.Inscripcion;
import com.nexum.entity.Usuario;
import com.nexum.exception.ConflictoException;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.exception.SolicitudInvalidaException;
import com.nexum.repository.CursoRepository;
import com.nexum.repository.InscripcionRepository;
import com.nexum.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InscripcionServiceImpl implements InscripcionService {

    private final InscripcionRepository inscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final AccesoService accesoService;
    private final com.nexum.repository.PagoRepository pagoRepository;

    @Override
    public InscripcionResponse crear(InscripcionRequest request) {
        accesoService.verificar(request.usuarioId());
        Usuario usuario = obtenerUsuario(request.usuarioId());
        Curso curso = obtenerCurso(request.cursoId());
        if (!Boolean.TRUE.equals(usuario.getEmailVerificado()) || usuario.getDeletedAt() != null) {
            throw new SolicitudInvalidaException("Verifica tu correo antes de inscribirte");
        }
        if (curso.getDeletedAt() != null) throw new SolicitudInvalidaException("Curso no disponible");
        com.nexum.entity.Pago pago = null;
        if (request.pagoId() != null) {
            pago = pagoRepository.findById(request.pagoId()).orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
            if (!pago.getUsuario().getId().equals(usuario.getId()) || pago.getCurso() == null || !pago.getCurso().getId().equals(curso.getId()))
                throw new SolicitudInvalidaException("El pago no corresponde al usuario y curso");
        }
        if (curso.getPrecio() != null && curso.getPrecio().signum()>0 &&
                (pago == null || pago.getEstado() == null || !java.util.Set.of("COMPLETADO", "APROBADO", "PAGADO").contains(pago.getEstado().toUpperCase(java.util.Locale.ROOT)) ||
                pago.getMonto().compareTo(curso.getPrecio())<0 || !pago.getMoneda().equals(curso.getMoneda()))) {
            throw new SolicitudInvalidaException("El curso requiere un pago aprobado por el importe y moneda correspondientes");
        }
        if (request.estado()!=null && !request.estado().equals("ACTIVA")) throw new SolicitudInvalidaException("La inscripción nueva debe estar ACTIVA");

        if (inscripcionRepository.existsByUsuario_IdAndCurso_Id(request.usuarioId(), request.cursoId())) {
            throw new ConflictoException("Ya existe una inscripción para el usuario y curso indicados");
        }
        if (curso.getEstado() != EstadoCurso.PUBLICADO) {
            throw new SolicitudInvalidaException("Solo se permiten inscripciones a cursos PUBLICADO");
        }

        Inscripcion inscripcion = Inscripcion.builder()
                .usuario(usuario)
                .curso(curso)
                .pago(pago)
                .estado(request.estado() == null || request.estado().isBlank()
                        ? Inscripcion.ESTADO_ACTIVA
                        : request.estado())
                .progresoPorcentaje(BigDecimal.ZERO)
                .build();

        return InscripcionResponse.desde(inscripcionRepository.save(inscripcion));
    }

    @Override
    public List<InscripcionResponse> listar(UUID usuarioId, UUID cursoId) {
        List<Inscripcion> inscripciones;

        if (usuarioId != null && cursoId != null) {
            accesoService.verificar(usuarioId);
            inscripciones = inscripcionRepository.findByUsuario_IdAndCurso_Id(usuarioId, cursoId);
        } else if (usuarioId != null) {
            accesoService.verificar(usuarioId);
            inscripciones = inscripcionRepository.findByUsuario_Id(usuarioId);
        } else if (cursoId != null) {
            accesoService.exigirAdministrador();
            inscripciones = inscripcionRepository.findByCurso_Id(cursoId);
        } else {
            accesoService.exigirAdministrador();
            inscripciones = inscripcionRepository.findAll();
        }

        return inscripciones.stream()
                .map(InscripcionResponse::desde)
                .toList();
    }

    @Override
    public List<InscripcionResponse> listarPorEmail(String email) {
        accesoService.verificarEmail(email);
        return inscripcionRepository.buscarPorEmail(email)
                .stream()
                .map(InscripcionResponse::desde)
                .toList();
    }

    @Override
    public InscripcionResponse obtenerPorId(UUID id) {
        Inscripcion inscripcion = buscarOLanzar(id);
        accesoService.verificar(inscripcion.getUsuario().getId());
        return InscripcionResponse.desde(inscripcion);
    }

    @Override
    public InscripcionResponse actualizarEstado(UUID id, InscripcionPatchRequest request) {
        Inscripcion inscripcion = buscarOLanzar(id);
        accesoService.verificar(inscripcion.getUsuario().getId());
        if (!accesoService.esAdministrador() && !"CANCELADA".equals(request.estado())) {
            throw new org.springframework.security.access.AccessDeniedException("Solo puedes cancelar tu inscripción; el progreso lo calcula el sistema");
        }
        if ("COMPLETADA".equals(request.estado()) && inscripcion.getProgresoPorcentaje().compareTo(new BigDecimal("100")) != 0)
            throw new SolicitudInvalidaException("Completa todas las lecciones antes de finalizar el curso");
        if (request.estado() != null) {
            inscripcion.setEstado(request.estado());
        }
        return InscripcionResponse.desde(inscripcionRepository.save(inscripcion));
    }

    @Override
    public void eliminar(UUID id) {
        Inscripcion inscripcion = buscarOLanzar(id);
        accesoService.verificar(inscripcion.getUsuario().getId());
        inscripcion.setEstado("CANCELADA");
        inscripcionRepository.save(inscripcion);
    }

    private Inscripcion buscarOLanzar(UUID id) {
        return inscripcionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Inscripción no encontrada con id: " + id));
    }

    private Usuario obtenerUsuario(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con id: " + usuarioId));
    }

    private Curso obtenerCurso(UUID cursoId) {
        return cursoRepository.findById(cursoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Curso no encontrado con id: " + cursoId));
    }
}
