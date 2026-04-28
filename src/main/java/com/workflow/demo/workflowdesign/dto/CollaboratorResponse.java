package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.workflow.demo.domain.entity.WorkflowCollaborator;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorRole;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuestas de colaboradores
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaboratorResponse {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("workflowId")
    private String workflowId;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("invitedBy")
    private String invitedBy;
    
    @JsonProperty("invitedAt")
    private Instant invitedAt;
    
    @JsonProperty("acceptedAt")
    private Instant acceptedAt;

    /**
     * Convierte entidad a DTO
     */
    public static CollaboratorResponse from(WorkflowCollaborator collaborator) {
        if (collaborator == null) {
            return null;
        }
        
        return CollaboratorResponse.builder()
            .id(collaborator.getId().toHexString())
            .workflowId(collaborator.getWorkflowId() != null ? collaborator.getWorkflowId().toHexString() : null)
            .userId(collaborator.getUserId().toHexString())
            .role(collaborator.getRole() != null ? collaborator.getRole().name() : null)
            .status(collaborator.getStatus() != null ? collaborator.getStatus().name() : null)
            .invitedBy(collaborator.getInvitedBy() != null ? collaborator.getInvitedBy().toHexString() : null)
            .invitedAt(collaborator.getInvitedAt())
            .acceptedAt(collaborator.getAcceptedAt())
            .build();
    }
}
