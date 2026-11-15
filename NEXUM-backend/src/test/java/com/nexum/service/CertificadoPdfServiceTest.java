package com.nexum.service;
import com.nexum.entity.Certificado;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.file.*;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
class CertificadoPdfServiceTest {
 Certificado sample(String nombre,String curso){return Certificado.builder().nombreAlumno(nombre).tituloCurso(curso)
  .codigoVerificacion("A25B36C47D58E69F0011223344556677").fechaEmision(LocalDateTime.of(2026,10,25,10,30)).build();}
 @Test void generaPdfConAcentosYCodigoYEnlace() throws Exception {
  var service=new CertificadoPdfService("http://127.0.0.1:4306");
  var bytes=service.generar(sample("María José Velásquez","Redes y Ciberseguridad: fundamentos y práctica"));
  Path directory=Path.of("target/qa-certificados");Files.createDirectories(directory);Files.write(directory.resolve("certificado-ejemplo.pdf"),bytes);
  try(var doc=Loader.loadPDF(bytes)){
   assertThat(doc.getNumberOfPages()).isEqualTo(1);
   String text=new PDFTextStripper().getText(doc);
   assertThat(text).contains("María José Velásquez","Redes y Ciberseguridad", "25 de octubre de 2026", "A25B36C47D58E69F0011223344556677");
   assertThat(doc.getPage(0).getAnnotations()).hasSize(1);
   var annotation=(org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink)doc.getPage(0).getAnnotations().getFirst();
   assertThat(((org.apache.pdfbox.pdmodel.interactive.action.PDActionURI)annotation.getAction()).getURI()).contains("/verify-certificate?codigo=");
   ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,110),"png",directory.resolve("certificado-ejemplo.png").toFile());
  }
 }
 @Test void mantieneNombresYTitulosLargosEnUnaPagina() throws Exception {
  var nombre="María Fernández de Córdova y Velásquez ".repeat(5).trim();
  var curso="Fundamentos avanzados de redes, infraestructura, seguridad informática y administración de sistemas ".repeat(2).trim();
  Files.createDirectories(Path.of("target/qa-certificados"));
  byte[] bytes=new CertificadoPdfService("http://127.0.0.1:4306").generar(sample(nombre,curso));
  try(var doc=Loader.loadPDF(bytes)){
   assertThat(doc.getNumberOfPages()).isEqualTo(1);
   String text=new PDFTextStripper().getText(doc);assertThat(text.replaceAll("\\s+"," ")).contains(nombre,curso);
   ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,110),"png",Path.of("target/qa-certificados/certificado-texto-largo.png").toFile());
  }
 }
}
