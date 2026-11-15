package com.nexum.controller;
import com.nexum.dto.CursoResponse;
import com.nexum.entity.Curso;
import com.nexum.repository.CursoRepository;
import com.nexum.service.CursoService;
import com.nexum.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
@RestController
public class PortadaCursoController {
    private final CursoRepository cursos;
    private final CursoService servicio;
    private final Path directory;
    public PortadaCursoController(CursoRepository cursos, CursoService servicio,
            @Value("${app.course-cover-directory:./uploads/cursos}") String directory) {
        this.cursos=cursos; this.servicio=servicio;
        this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    private Curso buscar(UUID id) {
        servicio.obtenerPorId(id);
        return cursos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Curso no encontrado"));
    }
    @PostMapping(value="/api/cursos/{id}/portada",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CursoResponse subir(@PathVariable UUID id, @RequestParam("foto") MultipartFile foto) throws IOException {
        Curso curso=buscar(id);
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

        BufferedImage thumbnail=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);
        var graphics=thumbnail.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,1280,720);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale=Math.max(1280.0/original.getWidth(),720.0/original.getHeight());
            int w=(int)Math.ceil(original.getWidth()*scale),h=(int)Math.ceil(original.getHeight()*scale);
            graphics.drawImage(original,(1280-w)/2,(720-h)/2,w,h,null);
        } finally { graphics.dispose(); }
        Files.createDirectories(directory);
        UUID imageId=UUID.randomUUID();
        Path target=directory.resolve(imageId+".jpg");
        String anterior=curso.getPortadaUrl();
        try {
            if (!ImageIO.write(thumbnail,"jpg",target.toFile())) throw new IOException("No se pudo guardar la portada");
            curso.setPortadaUrl("/api/cursos/"+id+"/portada/"+imageId+".jpg");
            return CursoResponse.desde(cursos.saveAndFlush(curso));
        } catch (RuntimeException | IOException ex) {
            curso.setPortadaUrl(anterior); Files.deleteIfExists(target); throw ex;
        }
    }
    @DeleteMapping("/api/cursos/{id}/portada")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CursoResponse quitar(@PathVariable UUID id) {
        Curso curso=buscar(id); curso.setPortadaUrl(null);
        return CursoResponse.desde(cursos.saveAndFlush(curso));
    }
    @GetMapping(value="/api/cursos/{id}/portada/{imageId}.jpg",produces=MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> imagen(@PathVariable UUID id,@PathVariable UUID imageId) throws IOException {
        Curso curso=buscar(id);
        String url="/api/cursos/"+id+"/portada/"+imageId+".jpg";
        if (!url.equals(curso.getPortadaUrl())) return ResponseEntity.notFound().build();
        Path target=directory.resolve(imageId+".jpg");
        if (!Files.isRegularFile(target)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-cache")
                .header("X-Content-Type-Options","nosniff").contentType(MediaType.IMAGE_JPEG).body(Files.readAllBytes(target));
    }
}
