package com.nexum.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.LocalDateTime;
import java.math.BigDecimal;
@Entity @Table(name="intento_examen") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IntentoExamen {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="examen_id",nullable=false) private Examen examen;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="usuario_id",nullable=false) private Usuario usuario;
 @Column(name="numero_intento",nullable=false) private Integer numeroIntento;
 private BigDecimal puntaje;
 private Boolean aprobado;
 @Column(name="iniciado_en",nullable=false) private LocalDateTime iniciadoEn;
 @Column(name="finalizado_en") private LocalDateTime finalizadoEn;
}
