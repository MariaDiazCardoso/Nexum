package com.nexum.service;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.dto.*;
import com.nexum.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.time.LocalDateTime;
import java.math.*;
@Service @RequiredArgsConstructor
public class EvaluacionService {
 private final jakarta.persistence.EntityManager entityManager;
 private final ExamenRepository examenes;
 private final PreguntaRepository preguntas;
 private final IntentoExamenRepository intentos;
 private final RespuestaIntentoRepository respuestas;
 private final LeccionRepository lecciones;
 private final UsuarioRepository usuarios;
 private final AccesoService acceso;
 private final AccesoCursoService accesoCurso;
 public record IntentoResponse(UUID id,int numero,BigDecimal puntaje,Boolean aprobado,LocalDateTime finalizadoEn) {
  static IntentoResponse desde(IntentoExamen i){return new IntentoResponse(i.getId(),i.getNumeroIntento(),i.getPuntaje(),i.getAprobado(),i.getFinalizadoEn());}
 }
 public record Estado(ExamenResponse examen,List<PreguntaResponse> preguntas,List<IntentoResponse> intentos,boolean aprobado,boolean puedeEditar) {}
 public record Revision(UUID preguntaId,UUID opcionId,boolean correcta,String explicacion) {}
 public record Resultado(IntentoResponse intento,List<Revision> respuestas) {}
 @Transactional(readOnly=true)
 public Estado estado(UUID leccionId) {
  var l=lecciones.findById(leccionId).orElseThrow(() -> new RecursoNoEncontradoException("Lección no encontrada"));
  accesoCurso.exigir(l.getModulo().getCurso());
  var e=examenes.findByLeccion_Id(leccionId).stream().findFirst().orElse(null);
  boolean admin=acceso.esAdministrador();
  if(e==null || (!admin && !Boolean.TRUE.equals(e.getPublicado()))) return new Estado(null,List.of(),List.of(),false,false);
  var lista=admin?List.<IntentoExamen>of():intentos.findByExamen_IdAndUsuario_IdOrderByNumeroIntentoAsc(e.getId(),acceso.obtenerIdUsuarioAutenticado());
  return new Estado(ExamenResponse.desde(e),ordenar(e.getId()).stream().map(p -> PreguntaResponse.desde(p,admin)).toList(),
   lista.stream().map(IntentoResponse::desde).toList(),lista.stream().anyMatch(i -> Boolean.TRUE.equals(i.getAprobado())),admin&&!intentos.existsByExamen_Id(e.getId()));
 }
 private List<Pregunta> ordenar(UUID id){return preguntas.findByExamen_Id(id).stream().sorted(Comparator.comparing(Pregunta::getId)).toList();}
 private void validarPreguntas(UUID id) {
  var lista=ordenar(id);
  if(lista.isEmpty() || lista.size()>200 || lista.stream().anyMatch(p -> p.getOpciones().size()<2 || p.getOpciones().stream().filter(o -> Boolean.TRUE.equals(o.getEsCorrecta())).count()!=1))
   throw new SolicitudInvalidaException("La evaluación requiere entre 1 y 200 preguntas, cada una con al menos dos opciones y una única respuesta correcta.");
 }
 @Transactional
 public Estado publicar(UUID id,boolean publicado) {
  acceso.exigirAdministrador();var e=examenes.bloquearPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Evaluación no encontrada"));
  if(intentos.existsByExamen_Id(id)) throw new ConflictoException("La evaluación tiene intentos y su publicación debe conservarse.");
  if(publicado) validarPreguntas(id);
  e.setPublicado(publicado);examenes.saveAndFlush(e);return estado(e.getLeccion().getId());
 }
 @Transactional
 public Estado ampliarIntentos(UUID id,Integer maximo) {
  acceso.exigirAdministrador();var e=examenes.bloquearPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Evaluación no encontrada"));
  Integer anterior=e.getIntentosMaximos();
  if(anterior==null || (maximo!=null && maximo<=anterior)) throw new SolicitudInvalidaException("Indica un límite mayor al actual o deja vacío para permitir intentos ilimitados.");
  e.setIntentosMaximos(maximo);examenes.saveAndFlush(e);return estado(e.getLeccion().getId());
 }
 @Transactional
 public IntentoResponse iniciar(UUID id) {
  if(acceso.esAdministrador()) throw new AccessDeniedException("El administrador gestiona las evaluaciones; los intentos corresponden a los alumnos.");
  var e=examenes.bloquearPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("Evaluación no encontrada"));
  accesoCurso.exigir(e.getLeccion().getModulo().getCurso());
  if(!Boolean.TRUE.equals(e.getPublicado())) throw new SolicitudInvalidaException("La evaluación todavía no está publicada.");
  validarPreguntas(id);UUID uid=acceso.obtenerIdUsuarioAutenticado();
  var lista=intentos.findByExamen_IdAndUsuario_IdOrderByNumeroIntentoAsc(id,uid);
  if(lista.stream().anyMatch(i -> Boolean.TRUE.equals(i.getAprobado()))) throw new ConflictoException("Ya aprobaste esta evaluación.");
  var abierto=lista.stream().filter(i -> i.getFinalizadoEn()==null).findFirst();if(abierto.isPresent()) return IntentoResponse.desde(abierto.get());
  int numero=lista.stream().mapToInt(IntentoExamen::getNumeroIntento).max().orElse(0)+1;
  if(e.getIntentosMaximos()!=null && numero>e.getIntentosMaximos()) throw new ConflictoException("Ya utilizaste los intentos permitidos.");
  var usuario=usuarios.findById(uid).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
  var i=IntentoExamen.builder().examen(e).usuario(usuario).numeroIntento(numero).iniciadoEn(LocalDateTime.now()).build();
  return IntentoResponse.desde(intentos.saveAndFlush(i));
 }
 @Transactional
 public Resultado enviar(UUID intentoId,EnviarEvaluacionRequest request) {
  var original=intentos.findById(intentoId).orElseThrow(() -> new RecursoNoEncontradoException("Intento no encontrado"));
  acceso.verificar(original.getUsuario().getId());
  if(acceso.esAdministrador()) throw new AccessDeniedException("Los intentos corresponden a los alumnos.");
  var e=examenes.bloquearPorId(original.getExamen().getId()).orElseThrow();
  var i=intentos.findById(intentoId).orElseThrow();
  entityManager.refresh(i);
  accesoCurso.exigir(e.getLeccion().getModulo().getCurso());
  if(i.getFinalizadoEn()!=null) throw new ConflictoException("Este intento ya fue enviado.");
  var lista=ordenar(e.getId());var mapa=new HashMap<UUID,UUID>();
  for(var r:request.respuestas()) if(mapa.put(r.preguntaId(),r.opcionId())!=null) throw new SolicitudInvalidaException("No repitas preguntas en el envío.");
  if(lista.size()!=mapa.size() || lista.stream().anyMatch(p -> !mapa.containsKey(p.getId()))) throw new SolicitudInvalidaException("Responde todas las preguntas de esta evaluación.");
  var guardadas=new ArrayList<RespuestaIntento>();int aciertos=0;
  for(var p:lista){var o=p.getOpciones().stream().filter(op -> op.getId().equals(mapa.get(p.getId()))).findFirst()
    .orElseThrow(() -> new SolicitudInvalidaException("Una respuesta no pertenece a su pregunta."));
   boolean correcta=Boolean.TRUE.equals(o.getEsCorrecta());if(correcta) aciertos++;
   guardadas.add(RespuestaIntento.builder().intento(i).pregunta(p).opcion(o).esCorrecta(correcta).build());
  }
  respuestas.saveAllAndFlush(guardadas);
  i.setPuntaje(BigDecimal.valueOf(aciertos).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(lista.size()),2,RoundingMode.HALF_UP));
  i.setAprobado(i.getPuntaje().compareTo(e.getPuntajeMinimoAprobacion()==null?new BigDecimal("70"):e.getPuntajeMinimoAprobacion())>=0);
  i.setFinalizadoEn(LocalDateTime.now());intentos.saveAndFlush(i);
  return new Resultado(IntentoResponse.desde(i),guardadas.stream().map(r -> new Revision(r.getPregunta().getId(),r.getOpcion().getId(),r.getEsCorrecta(),Boolean.TRUE.equals(i.getAprobado())?r.getPregunta().getExplicacionRespuesta():null)).toList());
 }
}
