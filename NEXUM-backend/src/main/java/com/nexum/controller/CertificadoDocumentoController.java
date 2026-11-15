package com.nexum.controller;
import com.nexum.service.*;
import com.nexum.repository.CertificadoRepository;
import com.nexum.dto.CertificadoResponse;
import com.nexum.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
import java.io.IOException;
import java.time.LocalDateTime;
@RestController @RequestMapping("/api/certificados") @RequiredArgsConstructor
public class CertificadoDocumentoController {
 private final CertificadoRepository certificados;
 private final CertificadoPdfService pdf;
 private final EmisionCertificadoService emision;
 private final AccesoService acceso;
 public record CertificadoPublico(String codigo,String nombreAlumno,String curso,LocalDateTime fechaEmision) {}
 @PostMapping("/emitir-pendientes") public List<CertificadoResponse> pendientes(){return emision.emitirPendientesPropios();}
 @GetMapping("/{id:[0-9a-fA-F-]+}/pdf") public ResponseEntity<byte[]> descargar(@PathVariable UUID id) throws IOException {
  var c=certificados.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Certificado no encontrado"));
  acceso.verificar(c.getInscripcion().getUsuario().getId());
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("NEXUM-certificado-"+c.getCodigoVerificacion()+".pdf").build().toString())
   .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(pdf.generar(c));
 }
 @GetMapping("/verificar/{codigo}") public CertificadoPublico verificar(@PathVariable String codigo) {
  String normalizado=codigo.trim().toUpperCase(Locale.ROOT);
  if(!normalizado.matches("[A-Z0-9-]{8,100}")) throw new RecursoNoEncontradoException("No encontramos un certificado con ese código.");
  var c=certificados.findByCodigoVerificacion(normalizado).orElseThrow(() -> new RecursoNoEncontradoException("No encontramos un certificado con ese código."));
  return new CertificadoPublico(c.getCodigoVerificacion(),c.getNombreAlumno(),c.getTituloCurso(),c.getFechaEmision());
 }
}
