package com.nexum.service;
import com.nexum.repository.*;
import com.nexum.entity.Examen;
import com.nexum.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class EvaluacionReglasService {
 private final ExamenRepository examenes;
 private final IntentoExamenRepository intentos;
 public void exigirAprobacion(UUID usuarioId,UUID leccionId) {
  if(examenes.existsByLeccion_IdAndPublicadoTrue(leccionId) && !intentos.existsByExamen_Leccion_IdAndUsuario_IdAndAprobadoTrue(leccionId,usuarioId))
   throw new SolicitudInvalidaException("Aprueba la evaluación de esta lección antes de marcarla como completada.");
 }
 public void exigirCursoAprobado(UUID usuarioId,UUID cursoId) {
  for(var e:examenes.findByLeccion_Modulo_Curso_IdAndPublicadoTrue(cursoId)) exigirAprobacion(usuarioId,e.getLeccion().getId());
 }
 public Examen bloquearEdicion(UUID examenId) {
  var e=examenes.bloquearPorId(examenId).orElseThrow(() -> new RecursoNoEncontradoException("Evaluación no encontrada"));
  if(intentos.existsByExamen_Id(examenId)) throw new ConflictoException("Esta evaluación ya tiene intentos y se conserva para mantener los resultados.");
  if(Boolean.TRUE.equals(e.getPublicado())) throw new ConflictoException("Retira la publicación antes de editar las preguntas o la configuración.");
  return e;
 }
}
