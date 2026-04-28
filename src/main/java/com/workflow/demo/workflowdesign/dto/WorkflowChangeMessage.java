package com.workflow.demo.workflowdesign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mensaje WebSocket para sincronización de cambios en tiempo real
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowChangeMessage {
    
    @JsonProperty("action")
    private String action; // "lock", "unlock", "node_added", "node_updated", "node_deleted", "edge_added", "edge_deleted", "design_saved"
    
    @JsonProperty("workflowId")
    private String workflowId;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("userName")
    private String userName;
    
    @JsonProperty("data")
    private Object data; // El nodo, arista, o diseño modificado
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("status")
    private String status; // "success", "error"
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("messageId")
    private String messageId;
    
    // Información de contexto
    @JsonProperty("nodeId")
    private String nodeId; // Para acciones sobre nodos específicos
    
    @JsonProperty("edgeId")
    private String edgeId; // Para acciones sobre aristas específicas
    
    @JsonProperty("laneId")
    private String laneId; // Para acciones sobre lanes específicas
    
    @JsonProperty("iteracion")
    private Integer iteracion; // Para iteraciones
}
