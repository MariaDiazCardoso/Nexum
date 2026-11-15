package com.nexum.controller;
import com.nexum.repository.CursoRepository;
import com.nexum.service.AccesoCursoService;
import com.nexum.service.CursoService;
import com.nexum.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/cursos")
@RequiredArgsConstructor
public class CursoAccesoController {
    private final CursoRepository cursos;
    private final CursoService servicio;
    private final AccesoCursoService acceso;
    public record AccesoResponse(UUID cursoId, boolean puedeAcceder) {}
    @GetMapping("/{id}/acceso")
    public AccesoResponse acceso(@PathVariable UUID id) {
        servicio.obtenerPorId(id);
        var curso=cursos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Curso no disponible"));
        return new AccesoResponse(id, acceso.puedeLeer(curso));
    }
}
