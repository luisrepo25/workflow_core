package com.workflow.demo.domain.embedded;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import com.workflow.demo.domain.enums.HistoryEventType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de historial para auditoría y trazabilidad
 * 
 * Fase 9: Auditoría y Trazabilidad
 * Registra todos los cambios en el workflow y las instancias para compliance
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryEvent {
    
    // Tipo de evento (actividad_completada, tramite_finalizado, etc)
    private HistoryEventType tipo;
    
    // Nodo específico del evento
    private String nodeId;
    
    // Múltiples nodos (para eventos paralelos)
    private List<String> nodeIds = new ArrayList<>();
    
    // Usuario que realizó la acción
    private ObjectId usuarioId;
    
    // Nombre del usuario para referencia rápida (auditoría)
    private String userName;
    
    // Descripción del evento
    private String detalle;
    
    // Timestamp del evento
    private Instant fecha;
    
    // Contexto de datos antes del cambio (para auditoría)
    private Map<String, Object> dataAnterior = new HashMap<>();
    
    // Contexto de datos después del cambio (para auditoría)
    private Map<String, Object> dataNueva = new HashMap<>();
    
    // Dirección IP del cliente (si aplica)
    private String ipAddress;
    
    // Resultado del evento: SUCCESS, FAILURE, PENDING
    private String status = "SUCCESS";
    
    // Mensaje de error si status es FAILURE
    private String errorMessage;
    
    /**
     * Factory method para crear un evento de auditoría estándar
     */
    public static HistoryEvent auditEvent(
        HistoryEventType tipo,
        ObjectId usuarioId,
        String userName,
        String detalle
    ) {
        return HistoryEvent.builder()
            .tipo(tipo)
            .usuarioId(usuarioId)
            .userName(userName)
            .detalle(detalle)
            .fecha(Instant.now())
            .status("SUCCESS")
            .build();
    }
    
    /**
     * Factory method para crear un evento de error
     */
    public static HistoryEvent errorEvent(
        HistoryEventType tipo,
        ObjectId usuarioId,
        String detalle,
        String errorMessage
    ) {
        return HistoryEvent.builder()
            .tipo(tipo)
            .usuarioId(usuarioId)
            .detalle(detalle)
            .fecha(Instant.now())
            .status("FAILURE")
            .errorMessage(errorMessage)
            .build();
    }
}