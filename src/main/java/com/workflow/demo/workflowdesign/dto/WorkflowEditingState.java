package com.workflow.demo.workflowdesign.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado actual de edición de un workflow para sincronización
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEditingState {
    
    private String workflowId;
    private String editedBy; // userId
    private String editedByName; // userName
    private long lockedAt;
    private long lastModified;
    
    // Usuarios activos editando
    private List<ActiveEditor> activeEditors;
    
    // Estado actual del diseño
    private Object lanes;
    private Object nodes;
    private Object edges;
    
    // Cambios pendientes no guardados
    private List<WorkflowChangeMessage> pendingChanges;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActiveEditor {
        private String userId;
        private String userName;
        private String email;
        private long connectedAt;
        private String cursorPosition; // Para indicador de cursor/selección
        private String selectedElement; // ID del elemento seleccionado
    }
}
