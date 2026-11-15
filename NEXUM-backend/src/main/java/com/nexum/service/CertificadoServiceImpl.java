package com.nexum.service;

import com.nexum.dto.CertificadoRequest;
import com.nexum.dto.CertificadoResponse;
import com.nexum.entity.Certificado;
import com.nexum.entity.Inscripcion;
import com.nexum.exception.ConflictoException;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.exception.SolicitudInvalidaException;
import com.nexum.repository.CertificadoRepository;
import com.nexum.repository.InscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificadoServiceImpl implements CertificadoService {

    private static final BigDecimal PROGRESO_COMPLETO = new BigDecimal("100.00");

    private final CertificadoRepository certificadoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final AccesoService accesoService;
    private final EvaluacionReglasService evaluaciones;
    private final com.nexum.repository.LeccionRepository leccionRepository;
    private final com.nexum.repository.ProgresoLeccionRepository progresoLeccionRepository;

    @Override
    public CertificadoResponse crear(CertificadoRequest request) {
        accesoService.exigirAdministrador();
        Inscripcion inscripcion = obtenerInscripcion(request.inscripcionId());
        validarProgresoCompleto(inscripcion);
        validarInscripcionSinCertificado(request.inscripcionId());

        Certificado certificado = Certificado.builder()
                .inscripcion(inscripcion)
                .nombreAlumno((inscripcion.getUsuario().getNombre()+" "+inscripcion.getUsuario().getApellido()).trim())
                .tituloCurso(inscripcion.getCurso().getTitulo())
                .codigoVerificacion(generarCodigoUnico())
                .urlPdf(request.urlPdf())
                .build();

        return CertificadoResponse.desde(certificadoRepository.save(certificado));
    }

    @Override
    public List<CertificadoResponse> listar(UUID usuarioId, String codigo) {
        if (codigo != null && !codigo.isBlank()) {
            Certificado certificado = certificadoRepository.findByCodigoVerificacion(codigo)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Certificado no encontrado con código: " + codigo));
            accesoService.verificar(certificado.getInscripcion().getUsuario().getId());
            return List.of(CertificadoResponse.desde(certificado));
        }

        List<Certificado> certificados;
        if (usuarioId != null) {
            accesoService.verificar(usuarioId);
            certificados = certificadoRepository.findByInscripcion_Usuario_Id(usuarioId);
        } else {
            accesoService.exigirAdministrador();
            certificados = certificadoRepository.findAll();
        }

        return certificados.stream()
                .map(CertificadoResponse::desde)
                .toList();
    }

    @Override
    public CertificadoResponse obtenerPorId(UUID id) {
        Certificado certificado = buscarOLanzar(id);
        accesoService.verificar(certificado.getInscripcion().getUsuario().getId());
        return CertificadoResponse.desde(certificado);
    }

    @Override
    public CertificadoResponse actualizar(UUID id, CertificadoRequest request) {
        accesoService.exigirAdministrador();
        Certificado certificado = buscarOLanzar(id);
        if (!certificado.getInscripcion().getId().equals(request.inscripcionId())) throw new ConflictoException("Un certificado emitido no puede asignarse a otra inscripción.");
        Inscripcion inscripcion = obtenerInscripcion(request.inscripcionId());
        validarProgresoCompleto(inscripcion);
        validarInscripcionSinOtroCertificado(request.inscripcionId(), id);

        certificado.setInscripcion(inscripcion);
        certificado.setUrlPdf(request.urlPdf());
        return CertificadoResponse.desde(certificadoRepository.save(certificado));
    }

    @Override
    public void eliminar(UUID id) {
        accesoService.exigirAdministrador();
        Certificado certificado = buscarOLanzar(id);
        certificadoRepository.delete(certificado);
    }

    private Certificado buscarOLanzar(UUID id) {
        return certificadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Certificado no encontrado con id: " + id));
    }

    private Inscripcion obtenerInscripcion(UUID inscripcionId) {
        return inscripcionRepository.findById(inscripcionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Inscripción no encontrada con id: " + inscripcionId));
    }

    private void validarProgresoCompleto(Inscripcion inscripcion) {
        evaluaciones.exigirCursoAprobado(inscripcion.getUsuario().getId(),inscripcion.getCurso().getId());
        BigDecimal progreso = inscripcion.getProgresoPorcentaje();
        long total = leccionRepository.contarPorCurso(inscripcion.getCurso().getId());
        long completas = progresoLeccionRepository.contarLeccionesCompletadas(inscripcion.getUsuario().getId(), inscripcion.getCurso().getId());
        if (total == 0 || total != completas) throw new SolicitudInvalidaException("Debes completar todas las lecciones antes de emitir el certificado");
        if (!"COMPLETADA".equals(inscripcion.getEstado()) || progreso == null || progreso.compareTo(PROGRESO_COMPLETO) != 0) {
            throw new SolicitudInvalidaException("Solo se puede emitir certificado con progreso del 100%");
        }
    }

    private void validarInscripcionSinCertificado(UUID inscripcionId) {
        if (certificadoRepository.existsByInscripcion_Id(inscripcionId)) {
            throw new ConflictoException("Ya existe un certificado para la inscripción indicada");
        }
    }

    private void validarInscripcionSinOtroCertificado(UUID inscripcionId, UUID id) {
        if (certificadoRepository.existsByInscripcion_IdAndIdNot(inscripcionId, id)) {
            throw new ConflictoException("Ya existe otro certificado para la inscripción indicada");
        }
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            codigo = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        } while (certificadoRepository.existsByCodigoVerificacion(codigo));
        return codigo;
    }
}
