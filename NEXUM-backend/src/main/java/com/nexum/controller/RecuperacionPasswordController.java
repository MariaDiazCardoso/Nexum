package com.nexum.controller;
import com.nexum.dto.*;
import com.nexum.service.RecuperacionPasswordService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public class RecuperacionPasswordController {
    private final RecuperacionPasswordService servicio;
    @PostMapping("/solicitar")
    public ResponseEntity<Void> solicitar(@Valid @RequestBody CorreoVerificacionRequest request, HttpServletRequest http) {
        servicio.solicitar(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/restablecer")
    public ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerPasswordRequest request, HttpServletRequest http) {
        servicio.restablecer(request.token(), request.password(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
