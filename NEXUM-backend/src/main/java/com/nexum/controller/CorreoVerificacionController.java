package com.nexum.controller;
import com.nexum.dto.*;
import com.nexum.service.CorreoVerificacionService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth/verificacion")
@RequiredArgsConstructor
public class CorreoVerificacionController {
    private final CorreoVerificacionService servicio;
    @PostMapping("/solicitar")
    public ResponseEntity<Void> solicitar(@Valid @RequestBody CorreoVerificacionRequest request, HttpServletRequest http) {
        servicio.solicitar(request.email(),http.getRemoteAddr()); return ResponseEntity.accepted().build();
    }
    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmar(@Valid @RequestBody ConfirmarCorreoRequest request) {
        servicio.confirmar(request.email(),request.codigo()); return ResponseEntity.noContent().build();
    }
}
