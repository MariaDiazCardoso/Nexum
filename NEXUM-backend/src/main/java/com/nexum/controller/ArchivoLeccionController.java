package com.nexum.controller;
import com.nexum.dto.*;
import com.nexum.entity.*;
import com.nexum.repository.*;
import com.nexum.service.AccesoCursoService;
import com.nexum.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@RestController
public class ArchivoLeccionController {
    private final LeccionRepository lecciones;
    private final RecursoAdjuntoRepository recursos;
    private final AccesoCursoService acceso;
    private final Path directory;
    public ArchivoLeccionController(LeccionRepository lecciones,RecursoAdjuntoRepository recursos,AccesoCursoService acceso,
            @Value("${app.lesson-files-directory:./uploads/lecciones}") String directory) {
        this.lecciones=lecciones;this.recursos=recursos;this.acceso=acceso;
        this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    private Leccion buscar(UUID id) {
        var l=lecciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lección no encontrada"));
        acceso.exigir(l.getModulo().getCurso());return l;
    }
    private void validar(MultipartFile file, boolean video) throws IOException {
        long limit=(video?100L:10L)*1024*1024;
        if(file.isEmpty() || file.getSize()>limit) throw new SolicitudInvalidaException(video
                ? "Elige un video MP4 de hasta 100 MB." : "Elige un documento PDF de hasta 10 MB.");
        byte[] head;
        try(var input=file.getInputStream()) { head=input.readNBytes(12); }
        boolean valido=video ? head.length>=12 && head[4]=='f' && head[5]=='t' && head[6]=='y' && head[7]=='p'
                : head.length>=5 && head[0]=='%' && head[1]=='P' && head[2]=='D' && head[3]=='F' && head[4]=='-';
        String name=Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT);
        if(!valido || !name.endsWith(video?".mp4":".pdf")) throw new SolicitudInvalidaException(video
                ? "El archivo debe ser un video MP4 válido." : "El archivo debe ser un documento PDF válido.");
    }
    private Path guardar(UUID fileId,String extension,MultipartFile file) throws IOException {
        Files.createDirectories(directory);
        Path target=directory.resolve(fileId+extension);
        try(var input=file.getInputStream()) { Files.copy(input,target); }
        catch(IOException e) { Files.deleteIfExists(target);throw e; }
        return target;
    }
    @PostMapping(value="/api/lecciones/{id}/video",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public LeccionResponse subirVideo(@PathVariable UUID id,@RequestParam("archivo") MultipartFile archivo) throws IOException {
        var l=buscar(id);validar(archivo,true);UUID fileId=UUID.randomUUID();Path target=guardar(fileId,".mp4",archivo);
        String anterior=l.getVideoUrl();
        try {
            l.setVideoUrl("/api/lecciones/"+id+"/video/"+fileId+".mp4");
            return LeccionResponse.desde(lecciones.saveAndFlush(l));
        } catch(RuntimeException e) { l.setVideoUrl(anterior);Files.deleteIfExists(target);throw e; }
    }
    @DeleteMapping("/api/lecciones/{id}/video")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public LeccionResponse quitarVideo(@PathVariable UUID id) {
        var l=buscar(id);l.setVideoUrl(null);return LeccionResponse.desde(lecciones.saveAndFlush(l));
    }
    @GetMapping("/api/lecciones/{id}/video/{fileId}.mp4")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> video(@PathVariable UUID id,@PathVariable UUID fileId) throws IOException {
        var l=buscar(id);
        if(!("/api/lecciones/"+id+"/video/"+fileId+".mp4").equals(l.getVideoUrl())) throw new RecursoNoEncontradoException("Video no disponible");
        return archivo(fileId,".mp4",MediaType.parseMediaType("video/mp4"),null);
    }
    @PostMapping(value="/api/lecciones/{id}/materiales",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public RecursoAdjuntoResponse subirMaterial(@PathVariable UUID id,@RequestParam("archivo") MultipartFile archivo) throws IOException {
        var l=buscar(id);validar(archivo,false);UUID fileId=UUID.randomUUID();Path target=guardar(fileId,".pdf",archivo);
        String name=Objects.toString(archivo.getOriginalFilename(),"Material.pdf").replaceAll("[\\p{Cntrl}/\\\\]","_");
        if(name.length()>255) name=name.substring(0,251)+".pdf";
        try {
            var r=RecursoAdjunto.builder().leccion(l).nombre(name).tipoArchivo("application/pdf")
                    .urlArchivo("/api/lecciones/"+id+"/materiales/"+fileId+".pdf").build();
            return RecursoAdjuntoResponse.desde(recursos.saveAndFlush(r));
        } catch(RuntimeException e) { Files.deleteIfExists(target);throw e; }
    }
    @GetMapping("/api/lecciones/{id}/materiales/{fileId}.pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> material(@PathVariable UUID id,@PathVariable UUID fileId) throws IOException {
        buscar(id);String url="/api/lecciones/"+id+"/materiales/"+fileId+".pdf";
        var recurso=recursos.findByLeccion_Id(id).stream().filter(r -> url.equals(r.getUrlArchivo())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Material no disponible"));
        return archivo(fileId,".pdf",MediaType.APPLICATION_PDF,recurso.getNombre());
    }
    private ResponseEntity<Resource> archivo(UUID id,String extension,MediaType type,String nombre) throws IOException {
        Path path=directory.resolve(id+extension);
        if(!Files.isRegularFile(path)) throw new RecursoNoEncontradoException("Archivo no disponible");
        var response=ResponseEntity.ok().contentType(type).contentLength(Files.size(path))
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff");
        if(nombre!=null) response.header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(nombre,StandardCharsets.UTF_8).build().toString());
        return response.body(new FileSystemResource(path));
    }
}
