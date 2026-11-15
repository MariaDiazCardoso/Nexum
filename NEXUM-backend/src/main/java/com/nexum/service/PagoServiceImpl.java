package com.nexum.service;

import com.nexum.dto.PagoRequest;
import com.nexum.dto.PagoResponse;
import com.nexum.entity.Pago;
import com.nexum.entity.SuscripcionIa;
import com.nexum.entity.Usuario;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.exception.SolicitudInvalidaException;
import com.nexum.repository.PagoRepository;
import com.nexum.repository.SuscripcionIaRepository;
import com.nexum.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {

    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SuscripcionIaRepository suscripcionIaRepository;
    private final AccesoService accesoService;
    private final com.nexum.repository.CursoRepository cursoRepository;

    @Override
    public PagoResponse crear(PagoRequest request) {
        accesoService.exigirAdministrador();
        Pago pago = new Pago();
        aplicar(pago, request);
        return PagoResponse.desde(pagoRepository.save(pago));
    }

    @Override
    public List<PagoResponse> listar(UUID usuarioId) {
        List<Pago> pagos;
        if (usuarioId != null) {
            accesoService.verificar(usuarioId);
            pagos = pagoRepository.findByUsuario_Id(usuarioId);
        } else {
            accesoService.exigirAdministrador();
            pagos = pagoRepository.findAll();
        }
        return pagos.stream().map(PagoResponse::desde).toList();
    }

    @Override
    public PagoResponse obtenerPorId(UUID id) {
        Pago pago = buscarOLanzar(id);
        accesoService.verificar(pago.getUsuario().getId());
        return PagoResponse.desde(pago);
    }

    @Override
    public PagoResponse actualizar(UUID id, PagoRequest request) {
        accesoService.exigirAdministrador();
        Pago pago = buscarOLanzar(id);
        aplicar(pago, request);
        return PagoResponse.desde(pagoRepository.save(pago));
    }

    @Override
    public void eliminar(UUID id) {
        accesoService.exigirAdministrador();
        pagoRepository.delete(buscarOLanzar(id));
    }

    private void aplicar(Pago pago, PagoRequest request) {
        if ((request.cursoId() == null) == (request.suscripcionId() == null)) {
            throw new SolicitudInvalidaException("Indica exactamente un curso o una suscripción para el pago");
        }
        if (request.monto() == null || request.monto().signum() <= 0) throw new SolicitudInvalidaException("El monto debe ser positivo");
        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con id: " + request.usuarioId()));
        SuscripcionIa suscripcion = null;
        if (request.suscripcionId() != null) {
            suscripcion = suscripcionIaRepository.findById(request.suscripcionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Suscripción no encontrada con id: " + request.suscripcionId()));
            if (!suscripcion.getUsuario().getId().equals(request.usuarioId())) {
                throw new SolicitudInvalidaException(
                        "La suscripción debe pertenecer al mismo usuario del pago");
            }
        }
        pago.setUsuario(usuario);
        pago.setSuscripcion(suscripcion);
        pago.setCurso(request.cursoId() == null ? null : cursoRepository.findById(request.cursoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Curso no encontrado")));
        pago.setMoneda(request.moneda() == null ? "PEN" : request.moneda());
        pago.setMonto(request.monto());
        pago.setMetodo(request.metodo());
        pago.setEstado(request.estado().toUpperCase(java.util.Locale.ROOT));
        pago.setConcepto(request.concepto());
        pago.setReferencia(request.referencia());
    }

    private Pago buscarOLanzar(UUID id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Pago no encontrado con id: " + id));
    }
}
