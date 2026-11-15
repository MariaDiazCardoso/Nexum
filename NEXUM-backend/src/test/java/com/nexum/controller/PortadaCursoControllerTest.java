package com.nexum.controller;
import com.nexum.entity.*;
import com.nexum.repository.CursoRepository;
import com.nexum.service.CursoService;
import com.nexum.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PortadaCursoControllerTest {
    @TempDir Path directory;
    CursoRepository cursos=mock(CursoRepository.class);
    CursoService servicio=mock(CursoService.class);
    Curso curso=Curso.builder().id(UUID.randomUUID()).titulo("Curso").build();
    PortadaCursoController controller;
    @BeforeEach void init() {
        controller=new PortadaCursoController(cursos,servicio,directory.toString());
        when(cursos.findById(curso.getId())).thenReturn(Optional.of(curso));
        lenient().when(cursos.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }
    MockMultipartFile imagen() throws Exception {
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("foto","nombre-no-confiable.png","image/png",bytes.toByteArray());
    }
    @Test void cargaSirveYQuitaPortadaConFormatoHorizontal() throws Exception {
        var result=controller.subir(curso.getId(),imagen());
        assertThat(result.portadaUrl()).startsWith("/api/cursos/"+curso.getId()+"/portada/").endsWith(".jpg");
        Path path;
        try(var files=Files.list(directory)) { path=files.findFirst().orElseThrow(); }
        var image=ImageIO.read(path.toFile());
        assertThat(image.getWidth()).isEqualTo(1280); assertThat(image.getHeight()).isEqualTo(720);
        UUID imageId=UUID.fromString(path.getFileName().toString().replace(".jpg",""));
        assertThat(controller.imagen(curso.getId(),imageId).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.quitar(curso.getId()).portadaUrl()).isNull();
        assertThat(controller.imagen(curso.getId(),imageId).getStatusCode().value()).isEqualTo(404);
    }
    @Test void rechazaArchivosFalsosYDemasiadoGrandes() {
        assertThatThrownBy(() -> controller.subir(curso.getId(),new MockMultipartFile("foto","falsa.png","image/png","archivo falso".getBytes())))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> controller.subir(curso.getId(),new MockMultipartFile("foto","grande.png","image/png",new byte[5*1024*1024+1])))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(cursos,never()).saveAndFlush(any());
    }
    @Test void limpiaElArchivoSiFallaLaPersistencia() throws Exception {
        when(cursos.saveAndFlush(any())).thenThrow(new IllegalStateException("fallo"));
        assertThatThrownBy(() -> controller.subir(curso.getId(),imagen())).isInstanceOf(IllegalStateException.class);
        try(var files=Files.list(directory)) { assertThat(files.count()).isZero(); }
        assertThat(curso.getPortadaUrl()).isNull();
    }
    @Test void noSirveUnaImagenDeOtroCurso() throws Exception {
        controller.subir(curso.getId(),imagen());
        assertThat(controller.imagen(curso.getId(),UUID.randomUUID()).getStatusCode().value()).isEqualTo(404);
    }
}
