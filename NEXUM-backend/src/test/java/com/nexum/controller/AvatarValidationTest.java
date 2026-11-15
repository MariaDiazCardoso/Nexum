package com.nexum.controller;
import com.nexum.service.UsuarioService;
import com.nexum.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AvatarValidationTest {
    @TempDir Path directory;
    @Test void rechazaArchivoFalsoAunqueSeLlamePng() {
        var c=new AvatarController(mock(UsuarioService.class),directory.toString());
        var file=new MockMultipartFile("foto","foto.png","image/png","no es una imagen".getBytes());
        assertThatThrownBy(()->c.subir(UUID.randomUUID(),file)).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void rechazaMasDeCincoMb() {
        var c=new AvatarController(mock(UsuarioService.class),directory.toString());
        var file=new MockMultipartFile("foto","foto.png","image/png",new byte[5*1024*1024+1]);
        assertThatThrownBy(()->c.subir(UUID.randomUUID(),file)).isInstanceOf(SolicitudInvalidaException.class);
    }
    @Test void admitePngRealYGuardaUnaMiniatura() throws Exception {
        var c=new AvatarController(mock(UsuarioService.class),directory.toString());
        var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"png",bytes);
        c.subir(UUID.randomUUID(),new MockMultipartFile("foto","foto.png","image/png",bytes.toByteArray()));
        try(var files=java.nio.file.Files.list(directory)) { Path file=files.findFirst().orElseThrow(); var img=ImageIO.read(file.toFile()); assertThat(img.getWidth()).isEqualTo(512); }
    }
}
