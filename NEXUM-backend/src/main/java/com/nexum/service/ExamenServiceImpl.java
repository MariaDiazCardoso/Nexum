package com.nexum.service;

import com.nexum.dto.ExamenRequest;
import com.nexum.dto.ExamenResponse;
import com.nexum.entity.Examen;
import com.nexum.entity.Leccion;
import com.nexum.exception.ConflictoException;
import com.nexum.exception.RecursoNoEncontradoException;
import com.nexum.repository.ExamenRepository;
import com.nexum.repository.LeccionRepository;
import com.nexum.repository.PreguntaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamenServiceImpl implements ExamenService {

    private final ExamenRepository examenRepository;
    private final LeccionRepository leccionRepository;
    private final PreguntaRepository preguntaRepository;
    private final AccesoCursoService accesoCursoService;
    private final EvaluacionReglasService reglas;
    private final AccesoService acceso;

    @Override
    public ExamenResponse crear(ExamenRequest request) {
        Leccion leccion = obtenerLeccion(request.leccionId());
        validarLeccionSinExamen(request.leccionId());

        Examen examen = Examen.builder()
                .leccion(leccion)
                .titulo(request.titulo())
                .intentosMaximos(request.intentosMaximos())
                .puntajeMinimoAprobacion(request.puntajeMinimoAprobacion())
                .build();

        return ExamenResponse.desde(examenRepository.save(examen));
    }

    @Override
    public List<ExamenResponse> listar(UUID leccionId) {
        List<Examen> examenes = leccionId == null
                ? examenRepository.findAll()
                : examenRepository.findByLeccion_Id(leccionId);

        examenes.forEach(e -> accesoCursoService.exigir(e.getLeccion().getModulo().getCurso()));
        return examenes.stream()
                .filter(e -> acceso.esAdministrador() || Boolean.TRUE.equals(e.getPublicado()))
                .map(ExamenResponse::desde)
                .toList();
    }

    @Override
    public ExamenResponse obtenerPorId(UUID id) {
        Examen examen = buscarOLanzar(id);
        accesoCursoService.exigir(examen.getLeccion().getModulo().getCurso());
        if (!acceso.esAdministrador() && !Boolean.TRUE.equals(examen.getPublicado())) throw new RecursoNoEncontradoException("Evaluación no disponible");
        return ExamenResponse.desde(examen);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ExamenResponse actualizar(UUID id, ExamenRequest request) {
        Examen examen = reglas.bloquearEdicion(id);
        if(!examen.getLeccion().getId().equals(request.leccionId())) throw new com.nexum.exception.SolicitudInvalidaException("No se puede mover la evaluación a otra lección.");
        Leccion leccion = obtenerLeccion(request.leccionId());
        validarLeccionSinOtroExamen(request.leccionId(), id);

        examen.setLeccion(leccion);
        examen.setTitulo(request.titulo());
        examen.setIntentosMaximos(request.intentosMaximos());
        examen.setPuntajeMinimoAprobacion(request.puntajeMinimoAprobacion());

        return ExamenResponse.desde(examenRepository.save(examen));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void eliminar(UUID id) {
        Examen examen = reglas.bloquearEdicion(id);
        if (preguntaRepository.existsByExamen_Id(id)) {
            throw new ConflictoException("No se puede eliminar el examen porque tiene preguntas asociadas");
        }
        examenRepository.delete(examen);
    }

    private Examen buscarOLanzar(UUID id) {
        return examenRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Examen no encontrado con id: " + id));
    }

    private Leccion obtenerLeccion(UUID leccionId) {
        return leccionRepository.findById(leccionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Lección no encontrada con id: " + leccionId));
    }

    private void validarLeccionSinExamen(UUID leccionId) {
        if (examenRepository.existsByLeccion_Id(leccionId)) {
            throw new ConflictoException("Ya existe un examen para la lección indicada");
        }
    }

    private void validarLeccionSinOtroExamen(UUID leccionId, UUID id) {
        if (examenRepository.existsByLeccion_IdAndIdNot(leccionId, id)) {
            throw new ConflictoException("Ya existe otro examen para la lección indicada");
        }
    }
}
