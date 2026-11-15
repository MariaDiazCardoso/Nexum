package com.nexum.controller;
import com.nexum.service.UsuarioService;
import com.nexum.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvatarControllerTest {
    @TempDir Path directory;
    @Test void storesNormalizedImageAndUpdatesProfile() throws Exception {
        var users = mock(UsuarioService.class);
        var controller = new AvatarController(users, directory.toString());
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var id = UUID.randomUUID();
        controller.subir(id, new MockMultipartFile("foto", "../photo.png", "image/png", bytes.toByteArray()));
        try (var files = Files.list(directory)) {
            var file = files.findFirst().orElseThrow();
            var image = ImageIO.read(file.toFile());
            assertEquals(512, image.getWidth()); assertEquals(512, image.getHeight());
            verify(users).actualizarParcial(eq(id), argThat(r -> r.avatarUrl().equals("/api/avatares/" + file.getFileName())));
            assertEquals(200, controller.imagen(UUID.fromString(file.getFileName().toString().replace(".png", ""))).getStatusCode().value());
        }
    }
    @Test void rejectsFakeImage() {
        var users = mock(UsuarioService.class);
        var controller = new AvatarController(users, directory.toString());
        assertThrows(SolicitudInvalidaException.class, () -> controller.subir(UUID.randomUUID(),
                new MockMultipartFile("foto", "photo.png", "image/png", "not an image".getBytes())));
        verify(users, never()).actualizarParcial(any(), any());
    }
    @Test void missingImageReturns404() throws Exception {
        var controller = new AvatarController(mock(UsuarioService.class), directory.toString());
        assertEquals(404, controller.imagen(UUID.randomUUID()).getStatusCode().value());
    }
}
