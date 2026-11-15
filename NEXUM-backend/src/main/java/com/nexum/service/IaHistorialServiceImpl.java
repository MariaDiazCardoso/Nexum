package com.nexum.service;

import com.nexum.dto.IaHistorialRequest;
import com.nexum.dto.IaHistorialResponse;
import com.nexum.entity.*;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fachada sobre ia_conversacion + ia_mensaje + ia_conversacion_contexto.
 * Mantiene el endpoint /api/historial-ia compatible a nivel de uso.
 */
@Service
@RequiredArgsConstructor
public class IaHistorialServiceImpl implements IaHistorialService {

    private final IaMensajeRepository iaMensajeRepository;
    private final IaConversacionRepository iaConversacionRepository;
    private final IaConversacionContextoRepository contextoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final LeccionRepository leccionRepository;
    private final AccesoService accesoService;
    private final AccesoCursoService accesoCursoService;

    @Override
    @Transactional
    public IaHistorialResponse crear(IaHistorialRequest request) {
        accesoService.verificar(request.usuarioId());

        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con id: " + request.usuarioId()));

        IaConversacion conversacion = resolverConversacion(request, usuario);
        asegurarContexto(conversacion, request);

        IaMensaje mensaje = IaMensaje.builder()
                .conversacion(conversacion)
                .rol(request.rol())
                .mensaje(request.mensaje())
                .build();

        IaMensaje guardado = iaMensajeRepository.save(mensaje);
        IaConversacionContexto ctx = contextoRepository.findFirstByConversacion_Id(conversacion.getId())
                .orElse(null);
        return IaHistorialResponse.desde(guardado, ctx);
    }

    @Override
    public List<IaHistorialResponse> listar(UUID usuarioId, UUID cursoId, UUID leccionId) {
        if (usuarioId != null) {
            accesoService.verificar(usuarioId);
        } else {
            accesoService.exigirAdministrador();
        }

        return iaMensajeRepository.buscarConFiltros(usuarioId, cursoId, leccionId)
                .stream()
                .map(m -> {
                    IaConversacionContexto ctx = contextoRepository
                            .findFirstByConversacion_Id(m.getConversacion().getId())
                            .orElse(null);
                    return IaHistorialResponse.desde(m, ctx);
                })
                .toList();
    }

    @Override
    public IaHistorialResponse obtenerPorId(UUID id) {
        IaMensaje m = buscarMensaje(id);
        accesoService.verificar(m.getConversacion().getUsuario().getId());
        IaConversacionContexto ctx = contextoRepository
                .findFirstByConversacion_Id(m.getConversacion().getId())
                .orElse(null);
        return IaHistorialResponse.desde(m, ctx);
    }

    @Override
    @Transactional
    public IaHistorialResponse actualizar(UUID id, IaHistorialRequest request) {
        IaMensaje m = buscarMensaje(id);
        accesoService.verificar(m.getConversacion().getUsuario().getId());
        m.setRol(request.rol());
        m.setMensaje(request.mensaje());
        IaMensaje guardado = iaMensajeRepository.save(m);
        asegurarContexto(m.getConversacion(), request);
        IaConversacionContexto ctx = contextoRepository
                .findFirstByConversacion_Id(m.getConversacion().getId())
                .orElse(null);
        return IaHistorialResponse.desde(guardado, ctx);
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        IaMensaje m = buscarMensaje(id);
        accesoService.verificar(m.getConversacion().getUsuario().getId());
        iaMensajeRepository.delete(m);
    }

    private IaConversacion resolverConversacion(IaHistorialRequest request, Usuario usuario) {
        if (request.conversacionId() != null) {
            IaConversacion c = iaConversacionRepository.findById(request.conversacionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Conversación no encontrada con id: " + request.conversacionId()));
            if (!c.getUsuario().getId().equals(usuario.getId())) {
                throw new RecursoNoEncontradoException("Conversación no pertenece al usuario");
            }
            return c;
        }
        return iaConversacionRepository.save(IaConversacion.builder()
                .usuario(usuario)
                .titulo("Chat IA")
                .build());
    }

    private void asegurarContexto(IaConversacion conversacion, IaHistorialRequest request) {
        if (request.cursoId() == null && request.leccionId() == null) {
            return;
        }
        IaConversacionContexto ctx = contextoRepository.findFirstByConversacion_Id(conversacion.getId())
                .orElseGet(() -> IaConversacionContexto.builder().conversacion(conversacion).build());

        if (request.cursoId() != null) {
            Curso curso = cursoRepository.findById(request.cursoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Curso no encontrado con id: " + request.cursoId()));
            accesoCursoService.exigir(curso);
            ctx.setCurso(curso);
        }
        if (request.leccionId() != null) {
            Leccion leccion = leccionRepository.findById(request.leccionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Lección no encontrada con id: " + request.leccionId()));
            accesoCursoService.exigir(leccion.getModulo().getCurso());
            if (ctx.getCurso()!=null && !ctx.getCurso().getId().equals(leccion.getModulo().getCurso().getId()))
                throw new com.nexum.exception.SolicitudInvalidaException("La lección no pertenece al curso indicado");
            ctx.setLeccion(leccion);
        }
        contextoRepository.save(ctx);
    }

    private IaMensaje buscarMensaje(UUID id) {
        return iaMensajeRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Mensaje IA no encontrado con id: " + id));
    }
}
