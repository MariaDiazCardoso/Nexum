package com.nexum.controller;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.service.AccesoCursoService;
import com.nexum.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ArchivoLeccionControllerTest {
    @TempDir Path directory;
    LeccionRepository lecciones=mock(LeccionRepository.class);
    RecursoAdjuntoRepository recursos=mock(RecursoAdjuntoRepository.class);
    AccesoCursoService acceso=mock(AccesoCursoService.class);
    Curso curso=Curso.builder().id(UUID.randomUUID()).build();
    Leccion leccion=Leccion.builder().id(UUID.randomUUID()).modulo(Modulo.builder().curso(curso).build()).build();
    ArchivoLeccionController controller;
    List<RecursoAdjunto> archivos=new ArrayList<>();
    @BeforeEach void init() {
        controller=new ArchivoLeccionController(lecciones,recursos,acceso,directory.toString());
        when(lecciones.findById(leccion.getId())).thenReturn(Optional.of(leccion));
        lenient().when(lecciones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(recursos.saveAndFlush(any())).thenAnswer(i -> { RecursoAdjunto r=i.getArgument(0);r.setId(UUID.randomUUID());archivos.add(r);return r; });
        lenient().when(recursos.findByLeccion_Id(leccion.getId())).thenReturn(archivos);
    }
    MockMultipartFile video() {
        return new MockMultipartFile("archivo","video.mp4","video/mp4",new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m'});
    }
    MockMultipartFile pdf() { return new MockMultipartFile("archivo","Guía.pdf","application/pdf","%PDF-1.7\narchivo de prueba".getBytes()); }
    UUID archivoId(String url,String extension) { return UUID.fromString(url.substring(url.lastIndexOf('/')+1).replace(extension,"")); }
    @Test void guardaYRetiraReferenciaDeVideo() throws Exception {
        String url=controller.subirVideo(leccion.getId(),video()).videoUrl();
        UUID id=archivoId(url,".mp4");
        var response=controller.video(leccion.getId(),id);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("video/mp4");
        assertThat(response.getBody().contentLength()).isEqualTo(12);
        assertThat(controller.quitarVideo(leccion.getId()).videoUrl()).isNull();
        assertThatThrownBy(() -> controller.video(leccion.getId(),id)).isInstanceOf(RecursoNoEncontradoException.class);
    }
    @Test void guardaPdfYLoSirveComoDescarga() throws Exception {
        var result=controller.subirMaterial(leccion.getId(),pdf());
        var response=controller.material(leccion.getId(),archivoId(result.urlArchivo(),".pdf"));
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(result.nombre()).isEqualTo("Guía.pdf");
        archivos.clear();
        assertThatThrownBy(() -> controller.material(leccion.getId(),archivoId(result.urlArchivo(),".pdf"))).isInstanceOf(RecursoNoEncontradoException.class);
    }
    @Test void rechazaArchivosDisfrazados() {
        assertThatThrownBy(() -> controller.subirVideo(leccion.getId(),new MockMultipartFile("archivo","video.mp4","video/mp4","no es video".getBytes())))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> controller.subirMaterial(leccion.getId(),new MockMultipartFile("archivo","guia.pdf","application/pdf","<html>invalido</html>".getBytes())))
                .isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void noEntregaArchivosSinAccesoAlCurso() {
        doThrow(new org.springframework.security.access.AccessDeniedException("sin acceso")).when(acceso).exigir(curso);
        assertThatThrownBy(() -> controller.video(leccion.getId(),UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.material(leccion.getId(),UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void limpiaArchivoSiFallaLaBd() throws Exception {
        when(lecciones.saveAndFlush(any())).thenThrow(new IllegalStateException("fallo"));
        assertThatThrownBy(() -> controller.subirVideo(leccion.getId(),video())).isInstanceOf(IllegalStateException.class);
        try(var files=Files.list(directory)){assertThat(files.count()).isZero();}
        assertThat(leccion.getVideoUrl()).isNull();
    }
}
