package com.nexum.service;

import com.nexum.dto.SuscripcionRequest;
import com.nexum.dto.SuscripcionResponse;
import com.nexum.entity.PlanIa;
import com.nexum.entity.SuscripcionIa;
import com.nexum.entity.Usuario;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.exception.SolicitudInvalidaException;
import com.nexum.repository.ConsumoIaRepository;
import com.nexum.repository.PlanIaRepository;
import com.nexum.repository.SuscripcionIaRepository;
import com.nexum.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuscripcionServiceImpl implements SuscripcionService {

    private final SuscripcionIaRepository suscripcionIaRepository;
    private final PlanIaRepository planIaRepository;
    private final ConsumoIaRepository consumoIaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoService accesoService;

    @Override
    @Transactional
    public SuscripcionResponse crear(SuscripcionRequest request) {
        accesoService.exigirAdministrador();
        PlanIa plan = resolverPlan(request);
        validarFechas(request.fechaInicio() != null ? request.fechaInicio() : LocalDate.now(), request.fechaFin());

        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con id: " + request.usuarioId()));

        LocalDate inicio = request.fechaInicio() != null ? request.fechaInicio() : LocalDate.now();

        SuscripcionIa suscripcion = SuscripcionIa.builder()
                .usuario(usuario)
                .plan(plan)
                .estado(request.estado())
                .fechaInicio(inicio)
                .fechaFin(request.fechaFin())
                .renovacionAutomatica(Boolean.TRUE.equals(request.renovacionAutomatica()))
                .creditosAsignados(plan.getCreditosPorPeriodo())
                .precioCobrado(plan.getPrecio())
                .build();

        return mapear(suscripcionIaRepository.save(suscripcion));
    }

    @Override
    public List<SuscripcionResponse> listar(UUID usuarioId) {
        List<SuscripcionIa> lista;
        if (usuarioId != null) {
            accesoService.verificar(usuarioId);
            lista = suscripcionIaRepository.findByUsuario_Id(usuarioId);
        } else {
            accesoService.exigirAdministrador();
            lista = suscripcionIaRepository.findAll();
        }
        return lista.stream().map(this::mapear).toList();
    }

    @Override
    public SuscripcionResponse obtenerPorId(UUID id) {
        SuscripcionIa s = buscarOLanzar(id);
        accesoService.verificar(s.getUsuario().getId());
        return mapear(s);
    }

    @Override
    @Transactional
    public SuscripcionResponse actualizar(UUID id, SuscripcionRequest request) {
        accesoService.exigirAdministrador();
        SuscripcionIa s = buscarOLanzar(id);
        PlanIa plan = resolverPlan(request);
        validarFechas(request.fechaInicio() != null ? request.fechaInicio() : LocalDate.now(), request.fechaFin());

        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Usuario no encontrado con id: " + request.usuarioId()));

        s.setUsuario(usuario);
        s.setPlan(plan);
        s.setEstado(request.estado());
        if (request.fechaInicio() != null) {
            s.setFechaInicio(request.fechaInicio());
        }
        s.setFechaFin(request.fechaFin());
        s.setRenovacionAutomatica(Boolean.TRUE.equals(request.renovacionAutomatica()));
        s.setCreditosAsignados(plan.getCreditosPorPeriodo());
        s.setPrecioCobrado(plan.getPrecio());

        return mapear(suscripcionIaRepository.save(s));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        accesoService.exigirAdministrador();
        suscripcionIaRepository.delete(buscarOLanzar(id));
    }

    private SuscripcionResponse mapear(SuscripcionIa s) {
        int usados = consumoIaRepository.sumCreditosUsadosBySuscripcionId(s.getId());
        return SuscripcionResponse.desde(s, usados);
    }

    private PlanIa resolverPlan(SuscripcionRequest request) {
        if (request.planId() != null) {
            return planIaRepository.findById(request.planId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Plan IA no encontrado con id: " + request.planId()));
        }
        if (request.plan() != null && !request.plan().isBlank()) {
            return planIaRepository.findByNombreIgnoreCase(request.plan().trim())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Plan IA no encontrado con nombre: " + request.plan()));
        }
        throw new SolicitudInvalidaException("Debe indicar planId o plan (nombre)");
    }

    private void validarFechas(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && !fin.isAfter(inicio)) {
            throw new SolicitudInvalidaException("La fecha fin debe ser posterior a la fecha inicio");
        }
    }

    private SuscripcionIa buscarOLanzar(UUID id) {
        return suscripcionIaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Suscripción no encontrada con id: " + id));
    }
}
