package com.nexum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario_xp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioXp {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "xp_total", nullable = false)
    @Builder.Default
    private Integer xpTotal = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer nivel = 1;

    @Column(name = "racha_dias", nullable = false)
    @Builder.Default
    private Integer rachaDias = 0;

    @Column(name = "ultima_actividad")
    private LocalDate ultimaActividad;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
        if (xpTotal == null) xpTotal = 0;
        if (nivel == null) nivel = 1;
        if (rachaDias == null) rachaDias = 0;
    }
}