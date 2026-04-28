package com.workflow.demo.workflowdesign.controller;

import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workflow.demo.domain.entity.WorkflowCollaborator;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorRole;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorStatus;
import com.workflow.demo.service.AuthorizationService;
import com.workflow.demo.service.WorkflowCollaboratorService;
import com.workflow.demo.workflowdesign.dto.CollaboratorResponse;
import com.workflow.demo.workflowdesign.dto.InviteCollaboratorRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para gestión de colaboradores en workflows
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows/{workflowId}/collaborators")
@RequiredArgsConstructor
public class WorkflowCollaboratorController {

    private final WorkflowCollaboratorService collaboratorService;
    private final AuthorizationService authorizationService;

    /**
     * POST /api/workflows/{workflowId}/collaborators
     * Invita un usuario a ser colaborador del workflow
     * Roles: DESIGNER (propietario del workflow)
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_DESIGNER')")
    public ResponseEntity<CollaboratorResponse> inviteCollaborator(
            @PathVariable String workflowId,
            @Valid @RequestBody InviteCollaboratorRequest request,
            Authentication authentication) {
        
        ObjectId userId = new ObjectId(authentication.getName());
        ObjectId wfId = new ObjectId(workflowId);

        // ✅ Validar que el usuario sea propietario del workflow
        if (!authorizationService.canInviteCollaborators(wfId, userId)) {
            log.warn("❌ Usuario {} intenta invitar colaboradores a workflow que no le pertenece", userId.toHexString());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // ✅ Validar role
        CollaboratorRole role;
        try {
            role = CollaboratorRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("❌ Rol inválido: {}", request.getRole());
            return ResponseEntity.badRequest().build();
        }

        try {
            WorkflowCollaborator collaborator = collaboratorService.inviteUserByEmail(
                wfId, request.getEmail(), userId, role
            );
            log.info("👥 Usuario invitado por email {} al workflow {} como {}", 
                request.getEmail(), workflowId, role.getDisplayName());
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(CollaboratorResponse.from(collaborator));
        } catch (IllegalArgumentException e) {
            log.warn("⚠️  {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/workflows/{workflowId}/collaborators
     * Lista todos los colaboradores del workflow
     * Roles: DESIGNER (propietario o colaborador)
     */
    @GetMapping
    @PreAuthorize("hasRole('ROLE_DESIGNER')")
    public ResponseEntity<List<CollaboratorResponse>> listCollaborators(
            @PathVariable String workflowId,
            Authentication authentication) {
        
        ObjectId userId = new ObjectId(authentication.getName());
        ObjectId wfId = new ObjectId(workflowId);

        // ✅ Validar que el usuario pueda ver colaboradores
        if (!authorizationService.canViewWorkflow(wfId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<WorkflowCollaborator> collaborators = collaboratorService.getCollaboratorsByWorkflow(wfId);
        List<CollaboratorResponse> responses = collaborators.stream()
            .map(CollaboratorResponse::from)
            .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * POST /api/workflows/{workflowId}/collaborators/{collaboratorId}/accept
     * Acepta una invitación de colaboración
     * Roles: Cualquier usuario autenticado
     */
    @PostMapping("/{collaboratorId}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CollaboratorResponse> acceptInvitation(
            @PathVariable String workflowId,
            @PathVariable String collaboratorId,
            Authentication authentication) {
        
        ObjectId userId = new ObjectId(authentication.getName());
        ObjectId collId = new ObjectId(collaboratorId);

        try {
            WorkflowCollaborator collaborator = collaboratorService.acceptInvitation(collId, userId);
            log.info("✅ Usuario {} aceptó invitación al workflow {}", userId.toHexString(), workflowId);
            return ResponseEntity.ok(CollaboratorResponse.from(collaborator));
        } catch (IllegalArgumentException e) {
            log.warn("⚠️  {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/workflows/{workflowId}/collaborators/{collaboratorId}/reject
     * Rechaza una invitación de colaboración
     * Roles: Cualquier usuario autenticado
     */
    @PostMapping("/{collaboratorId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> rejectInvitation(
            @PathVariable String workflowId,
            @PathVariable String collaboratorId,
            Authentication authentication) {

        ObjectId userId = new ObjectId(authentication.getName());
        ObjectId collId = new ObjectId(collaboratorId);

        try {
            collaboratorService.rejectInvitation(collId, userId);
            log.info("❌ Usuario {} rechazó invitación al workflow {}", userId.toHexString(), workflowId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("⚠️  {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /api/workflows/{workflowId}/collaborators/{userId}
     * Remueve un colaborador del workflow
     * Roles: DESIGNER (propietario)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ROLE_DESIGNER')")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable String workflowId,
            @PathVariable String userId,
            Authentication authentication) {
        
        ObjectId ownerUserId = new ObjectId(authentication.getName());
        ObjectId wfId = new ObjectId(workflowId);
        ObjectId collUserId = new ObjectId(userId);

        // ✅ Validar que el usuario sea propietario
        if (!authorizationService.canInviteCollaborators(wfId, ownerUserId)) {
            log.warn("❌ Usuario {} intenta remover colaborador de workflow que no le pertenece", 
                ownerUserId.toHexString());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        collaboratorService.removeCollaborator(wfId, collUserId);
        log.info("🗑️  Usuario {} removido del workflow {}", collUserId.toHexString(), workflowId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/workflows/{workflowId}/collaborators/{userId}/role
     * Cambia el rol de un colaborador
     * Roles: DESIGNER (propietario)
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ROLE_DESIGNER')")
    public ResponseEntity<CollaboratorResponse> changeCollaboratorRole(
            @PathVariable String workflowId,
            @PathVariable String userId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        
        ObjectId ownerUserId = new ObjectId(authentication.getName());
        ObjectId wfId = new ObjectId(workflowId);
        ObjectId collUserId = new ObjectId(userId);

        // ✅ Validar que el usuario sea propietario
        if (!authorizationService.canInviteCollaborators(wfId, ownerUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // ✅ Validar rol
        CollaboratorRole newRole;
        try {
            newRole = CollaboratorRole.valueOf(request.get("role").toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        WorkflowCollaborator collaborator = collaboratorService.changeRole(wfId, collUserId, newRole, ownerUserId);
        log.info("🔄 Rol cambiado para usuario {} en workflow {}", collUserId.toHexString(), workflowId);
        return ResponseEntity.ok(CollaboratorResponse.from(collaborator));
    }
}
