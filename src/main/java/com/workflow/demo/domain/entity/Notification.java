package com.workflow.demo.domain.entity;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.workflow.demo.domain.embedded.NotificationReference;
import com.workflow.demo.domain.embedded.PushMeta;
import com.workflow.demo.domain.enums.NotificationStatus;
import com.workflow.demo.domain.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notificación de usuario (Fase 10)
 * Registra notificaciones para:
 * - Asignación de actividades
 * - Cambios de estado en procesos
 * - Completación de workflows
 * - Alertas de departamento
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "notifications")
public class Notification {
    
    @Id
    private ObjectId id;

    // Usuario destinatario
    @Indexed
    private ObjectId userId;

    // Tipo de notificación (email, SMS, push in-app, etc)
    private NotificationType tipo;
    
    // Título de la notificación
    private String titulo;
    
    // Cuerpo del mensaje
    private String mensaje;

    // Estado de la notificación (pendiente, enviada, fallida)
    @Indexed
    private NotificationStatus estado;

    // Referencia al proceso/workflow
    private NotificationReference referencia;
    
    // Metadata para push notifications
    private PushMeta pushMeta;
    
    // ¿Ha sido leída?
    private boolean leida = false;

    // Timestamps de auditoría
    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
    
    // Cuándo fue leída
    private Instant leIdoAt;
    
    // Cuándo fue enviada
    private Instant enviadoAt;
    
    // Canal por el que se envió (email, SMS, push, in-app)
    private String canal;
    
    // Motivo de falla si estado es FALLIDA
    private String motivoFalla;
    
    // Número de intentos de reenvío
    @Builder.Default
    private int intentosReenvio = 0;
    
    // IP del cliente cuando se leyó (si aplica)
    private String ipAddress;
    
    // User agent del cliente cuando se leyó (si aplica)
    private String userAgent;
}