package com.nexum.controller;

import com.nexum.dto.UsuarioPatchRequest;
import com.nexum.dto.UsuarioResponse;
import com.nexum.service.UsuarioService;
import com.nexum.exception.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@RestController
public class AvatarController {
    private final UsuarioService usuarios;
    private final Path directory;
    public AvatarController(UsuarioService usuarios,
            @Value("${app.avatar-directory:./uploads/avatares}") String directory) {
        this.usuarios = usuarios;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/api/usuarios/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR') or @accesoService.esPropio(#id)")
    public UsuarioResponse subir(@PathVariable UUID id, @RequestParam("foto") MultipartFile foto) throws IOException {
        usuarios.obtenerPorId(id);
        if (foto.isEmpty() || foto.getSize() > 5 * 1024 * 1024) {
            throw new SolicitudInvalidaException("Elige una foto JPG o PNG de hasta 5 MB.");
        }
        BufferedImage original;
        try (ImageInputStream stream = ImageIO.createImageInputStream(foto.getInputStream())) {
            if (stream == null) throw new SolicitudInvalidaException("El archivo no es una imagen válida.");
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new SolicitudInvalidaException("El archivo no es una imagen JPG o PNG válida.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) {
                    throw new SolicitudInvalidaException("Solo se permiten imágenes JPG o PNG.");
                }
                reader.setInput(stream);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > 20000000) {
                    throw new SolicitudInvalidaException("La imagen es demasiado grande. El máximo es 20 megapíxeles.");
                }
                original = reader.read(0);
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException e) {
            throw new SolicitudInvalidaException("No pudimos leer la imagen. Elige otra foto JPG o PNG.");
        }
        if (original == null) throw new SolicitudInvalidaException("El archivo no contiene una imagen válida.");
        int size = Math.min(original.getWidth(), original.getHeight());
        BufferedImage thumbnail = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        var graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int x = (original.getWidth() - size) / 2, y = (original.getHeight() - size) / 2;
            graphics.drawImage(original, 0, 0, 512, 512, x, y, x + size, y + size, null);
        } finally { graphics.dispose(); }
        Files.createDirectories(directory);
        UUID imageId = UUID.randomUUID();
        Path target = directory.resolve(imageId + ".png");
        ImageIO.write(thumbnail, "png", target.toFile());
        try {
            return usuarios.actualizarParcial(id, new UsuarioPatchRequest(null, null, null, "/api/avatares/" + imageId + ".png"));
        } catch (RuntimeException e) { Files.deleteIfExists(target); throw e; }
    }

    @DeleteMapping("/api/usuarios/{id}/avatar")
    @PreAuthorize("hasRole('ADMINISTRADOR') or @accesoService.esPropio(#id)")
    public UsuarioResponse quitar(@PathVariable UUID id) {
        return usuarios.actualizarParcial(id, new UsuarioPatchRequest(null, null, null, ""));
    }

    @GetMapping(value = "/api/avatares/{imageId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> imagen(@PathVariable UUID imageId) throws IOException {
        Path target = directory.resolve(imageId + ".png");
        if (!Files.isRegularFile(target)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(target));
    }
}
