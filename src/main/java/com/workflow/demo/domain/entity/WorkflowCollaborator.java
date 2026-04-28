package com.workflow.demo.domain.entity;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Documento para representar colaboradores en un workflow.
 * Permite que el propietario invite a otros diseñadores a editar un workflow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "workflow_collaborators")
@CompoundIndexes({
    @CompoundIndex(name = "workflow_user_idx", def = "{'workflowId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "workflow_idx", def = "{'workflowId': 1}")
})
public class WorkflowCollaborator {
    
    @Id
    private ObjectId id;
    
    // ✅ ID del workflow
    @JsonProperty("workflowId")
    private ObjectId workflowId;
    
    // ✅ ID del usuario invitado
    @JsonProperty("userId")
    private ObjectId userId;
    
    // ✅ Rol del colaborador en este workflow (DESIGNER o VIEWER)
    @JsonProperty("role")
    private CollaboratorRole role;  // DESIGNER = puede editar, VIEWER = solo lectura
    
    // ✅ ID del usuario que realizó la invitación
    @JsonProperty("invitedBy")
    private ObjectId invitedBy;
    
    // ✅ Estado de la invitación
    @JsonProperty("status")
    private CollaboratorStatus status;  // PENDING = esperando aceptación, ACCEPTED = aceptada
    
    // ✅ Timestamp de creación
    @CreatedDate
    @JsonProperty("invitedAt")
    private Instant invitedAt;
    
    // ✅ Timestamp de última modificación
    @LastModifiedDate
    @JsonProperty("updatedAt")
    private Instant updatedAt;
    
    // ✅ Timestamp de aceptación (si aplica)
    @JsonProperty("acceptedAt")
    private Instant acceptedAt;
    
    /**
     * Roles que un colaborador puede tener en un workflow
     */
    public enum CollaboratorRole {
        DESIGNER("Diseñador"),  // Puede editar el diseño
        VIEWER("Visor");        // Solo puede ver el diseño

        private final String displayName;

        CollaboratorRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Estados de una invitación de colaboración
     */
    public enum CollaboratorStatus {
        PENDING("Pendiente"),
        ACCEPTED("Aceptada"),
        REJECTED("Rechazada"),
        REMOVED("Removida");

        private final String displayName;

        CollaboratorStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
