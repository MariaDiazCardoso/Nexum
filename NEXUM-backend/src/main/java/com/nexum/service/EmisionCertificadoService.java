package com.nexum.service;
import com.nexum.dto.CertificadoResponse;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor
public class EmisionCertificadoService {
 private final CertificadoRepository certificados;
 private final InscripcionRepository inscripciones;
 private final LeccionRepository lecciones;
 private final ProgresoLeccionRepository progresos;
 private final EvaluacionReglasService evaluaciones;
 private final AccesoService acceso;
 @Transactional
 public Certificado emitirSiCorresponde(UUID usuarioId,UUID cursoId) {
  var i=inscripciones.bloquearInscripcion(usuarioId,cursoId).orElse(null);
  if(i==null) return null;
  var existente=certificados.findByInscripcion_Id(i.getId());if(existente.isPresent()) return existente.get();
  if(!"COMPLETADA".equals(i.getEstado()) || i.getProgresoPorcentaje()==null || i.getProgresoPorcentaje().compareTo(new BigDecimal("100"))!=0) return null;
  long total=lecciones.contarPorCurso(cursoId);
  if(total==0 || progresos.contarLeccionesCompletadas(usuarioId,cursoId)!=total) return null;
  try { evaluaciones.exigirCursoAprobado(usuarioId,cursoId); } catch(SolicitudInvalidaException e) { return null; }
  var u=i.getUsuario();
  var c=Certificado.builder().inscripcion(i).nombreAlumno((u.getNombre()+" "+u.getApellido()).trim())
    .tituloCurso(i.getCurso().getTitulo()).codigoVerificacion(UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT)).build();
  return certificados.saveAndFlush(c);
 }
 @Transactional
 public List<CertificadoResponse> emitirPendientesPropios() {
  UUID usuarioId=acceso.obtenerIdUsuarioAutenticado();
  if(acceso.esAdministrador()) throw new org.springframework.security.access.AccessDeniedException("Los certificados de aprendizaje corresponden a los alumnos.");
  for(var i:inscripciones.findByUsuario_Id(usuarioId)) if("COMPLETADA".equals(i.getEstado())) emitirSiCorresponde(usuarioId,i.getCurso().getId());
  return certificados.findByInscripcion_Usuario_Id(usuarioId).stream().map(CertificadoResponse::desde).toList();
 }
}
