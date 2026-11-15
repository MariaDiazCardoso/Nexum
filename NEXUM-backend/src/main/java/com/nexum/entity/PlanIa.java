package com.nexum.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plan_ia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanIa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(length = 3)
    @Builder.Default
    private String moneda = "PEN";

    @Column(name = "creditos_por_periodo", nullable = false)
    private Integer creditosPorPeriodo;

    @Column(nullable = false, length = 20)
    private String periodo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
