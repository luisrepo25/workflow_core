package com.workflow.demo.domain.embedded;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot del diseño de workflow capturado en el momento de crear un ProcessInstance
 * Asegura que el flujo de un trámite sea inmutable incluso si el workflow original cambia
 * 
 * Caso de uso:
 * - Cliente inicia trámite con workflow v1.0
 * - Después, admin actualiza workflow a v2.0 con nuevos nodos
 * - El trámite del cliente sigue v1.0 (su snapshot)
 * - Nuevos trámites usan v2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowSnapshot {
    
    // Versión del workflow al momento del snapshot
    private String workflowVersion;
    
    // Nodos del workflow (inmutable después de captura)
    private List<WorkflowNode> nodes;
    
    // Aristas del workflow (inmutable después de captura)
    private List<WorkflowEdge> edges;
    
    // Lanes del workflow (inmutable después de captura)
    private List<Lane> lanes;
    
    // Timestamp de cuando se capturó el snapshot
    private java.time.Instant capturedAt;
    
    // ID del usuario que creó el proceso (para auditoría)
    private org.bson.types.ObjectId capturedBy;
    
    /**
     * Crea un snapshot del estado actual de un workflow
     * 
     * @param workflow Workflow del que capturar el snapshot
     * @param userId Usuario que inicia el proceso
     * @return Snapshot del workflow
     */
    public static WorkflowSnapshot from(com.workflow.demo.domain.entity.Workflow workflow, 
                                       org.bson.types.ObjectId userId) {
        // Generar versión basada en timestamp (o usar un campo version del workflow)
        String version = workflow.getCodigo() + "-" + workflow.getUpdatedAt().getEpochSecond();
        
        return WorkflowSnapshot.builder()
            .workflowVersion(version)
            .nodes(workflow.getNodes())
            .edges(workflow.getEdges())
            .lanes(workflow.getLanes())
            .capturedAt(java.time.Instant.now())
            .capturedBy(userId)
            .build();
    }
    
    /**
     * Obtiene una lane específica del snapshot
     */
    public Lane getLaneById(String laneId) {
        if (lanes == null || laneId == null) return null;
        return lanes.stream()
            .filter(l -> l.getId().equals(laneId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Obtiene un nodo específico del snapshot
     */
    public WorkflowNode getNodeById(String nodeId) {
        if (nodes == null || nodeId == null) return null;
        return nodes.stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Obtiene las aristas salientes de un nodo
     */
    public List<WorkflowEdge> getOutgoingEdges(String nodeId) {
        if (edges == null || nodeId == null) return List.of();
        return edges.stream()
            .filter(e -> e.getFromNodeId().equals(nodeId))
            .toList();
    }
    
    /**
     * Obtiene las aristas entrantes a un nodo
     */
    public List<WorkflowEdge> getIncomingEdges(String nodeId) {
        if (edges == null || nodeId == null) return List.of();
        return edges.stream()
            .filter(e -> e.getToNodeId().equals(nodeId))
            .toList();
    }
}
