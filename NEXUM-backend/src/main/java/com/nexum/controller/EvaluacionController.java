package com.nexum.controller;
import com.nexum.service.EvaluacionService;
import com.nexum.dto.EnviarEvaluacionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/evaluaciones") @RequiredArgsConstructor
public class EvaluacionController {
 private final EvaluacionService servicio;
 public record Publicacion(@NotNull Boolean publicado) {}
 public record LimiteIntentos(@jakarta.validation.constraints.Min(1) Integer intentosMaximos) {}
 @PatchMapping("/{id}/intentos-maximos") public EvaluacionService.Estado ampliar(@PathVariable UUID id,@Valid @RequestBody LimiteIntentos r){return servicio.ampliarIntentos(id,r.intentosMaximos());}
 @GetMapping("/lecciones/{id}") public EvaluacionService.Estado estado(@PathVariable UUID id){return servicio.estado(id);}
 @PatchMapping("/{id}/publicacion") public EvaluacionService.Estado publicar(@PathVariable UUID id,@Valid @RequestBody Publicacion r){return servicio.publicar(id,r.publicado());}
 @PostMapping("/{id}/intentos") public EvaluacionService.IntentoResponse iniciar(@PathVariable UUID id){return servicio.iniciar(id);}
 @PostMapping("/intentos/{id}/respuestas") public EvaluacionService.Resultado enviar(@PathVariable UUID id,@Valid @RequestBody EnviarEvaluacionRequest r){return servicio.enviar(id,r);}
}
