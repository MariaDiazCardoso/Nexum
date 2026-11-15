package com.nexum.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="respuesta_intento") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RespuestaIntento {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="intento_id",nullable=false) private IntentoExamen intento;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pregunta_id",nullable=false) private Pregunta pregunta;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="opcion_id") private OpcionRespuesta opcion;
 @Column(name="es_correcta") private Boolean esCorrecta;
}
