package com.workflow.demo.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.repository.WorkflowRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de autorización basado en atributos (ABAC).
 * Valida si un usuario tiene permiso para realizar acciones específicas sobre recursos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowCollaboratorService collaboratorService;

    /**
     * Verifica si un usuario puede VER un workflow
     * - Owner siempre puede ver
     * - Colaboradores aceptados pueden ver
     * - Admin puede ver todo
     */
    public boolean canViewWorkflow(ObjectId workflowId, ObjectId userId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null) {
            log.warn("❌ Workflow no encontrado: {}", workflowId);
            return false;
        }

        // Owner siempre puede ver
        if (workflow.getCreatedBy() != null && workflow.getCreatedBy().equals(userId)) {
            log.debug("✅ Usuario {} es propietario del workflow {}", userId, workflowId);
            return true;
        }

        // Verificar si es colaborador
        if (collaboratorService.isAcceptedCollaborator(workflowId, userId)) {
            log.debug("✅ Usuario {} es colaborador aceptado del workflow {}", userId, workflowId);
            return true;
        }

        log.debug("❌ Usuario {} no tiene permiso para ver workflow {}", userId, workflowId);
        return false;
    }

    /**
     * Verifica si un usuario puede EDITAR un workflow
     * - Owner siempre puede editar
     * - Solo diseñadores invitados pueden editar
     * - Admin puede editar todo
     */
    public boolean canEditWorkflow(ObjectId workflowId, ObjectId userId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null) {
            log.warn("❌ Workflow no encontrado: {}", workflowId);
            return false;
        }

        // Owner siempre puede editar
        if (workflow.getCreatedBy() != null && workflow.getCreatedBy().equals(userId)) {
            log.debug("✅ Usuario {} es propietario del workflow {}", userId, workflowId);
            return true;
        }

        // Verificar si es diseñador aceptado
        boolean canEdit = collaboratorService.canEditWorkflow(workflowId, userId, workflow.getCreatedBy());
        if (canEdit) {
            log.debug("✅ Usuario {} es diseñador aceptado del workflow {}", userId, workflowId);
        } else {
            log.debug("❌ Usuario {} no tiene permiso para editar workflow {}", userId, workflowId);
        }
        return canEdit;
    }

    /**
     * Verifica si un usuario puede BLOQUEAR un workflow para edición
     * - Mismas reglas que canEditWorkflow
     */
    public boolean canLockWorkflow(ObjectId workflowId, ObjectId userId) {
        return canEditWorkflow(workflowId, userId);
    }

    /**
     * Verifica si un usuario puede DESBLOQUEAR un workflow
     * - Solo quien tiene el lock puede desbloquearlo
     * - O el owner (puede forzar desbloquearlo)
     */
    public boolean canUnlockWorkflow(ObjectId workflowId, ObjectId userId, ObjectId lockHolderId) {
        if (lockHolderId == null) {
            return true;  // No está bloqueado
        }

        // Quien tiene el lock puede desbloquearlo
        if (lockHolderId.equals(userId)) {
            return true;
        }

        // Owner puede forzar desbloqueo
        Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow != null && workflow.getCreatedBy() != null && workflow.getCreatedBy().equals(userId)) {
            log.info("🔓 Owner fuerza desbloqueo del workflow {}", workflowId);
            return true;
        }

        log.warn("❌ Usuario {} no puede desbloquear workflow {}", userId, workflowId);
        return false;
    }

    /**
     * Verifica si un usuario puede PUBLICAR un workflow
     * - Solo owner puede publicar
     */
    public boolean canPublishWorkflow(ObjectId workflowId, ObjectId userId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null) {
            log.warn("❌ Workflow no encontrado: {}", workflowId);
            return false;
        }

        boolean isOwner = workflow.getCreatedBy() != null && workflow.getCreatedBy().equals(userId);
        if (!isOwner) {
            log.warn("❌ Solo el owner puede publicar el workflow");
        }
        return isOwner;
    }

    /**
     * Verifica si un usuario puede INVITAR colaboradores a un workflow
     * - Solo owner puede invitar
     */
    public boolean canInviteCollaborators(ObjectId workflowId, ObjectId userId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null) {
            log.warn("❌ Workflow no encontrado: {}", workflowId);
            return false;
        }

        boolean isOwner = workflow.getCreatedBy() != null && workflow.getCreatedBy().equals(userId);
        if (!isOwner) {
            log.warn("❌ Solo el owner puede invitar colaboradores");
        }
        return isOwner;
    }

    /**
     * Verifica si un usuario puede ELIMINAR un workflow
     * - Solo owner puede eliminar
     */
    public boolean canDeleteWorkflow(ObjectId workflowId, ObjectId userId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
        if (workflow == null) {
            log.warn("❌ Workflow no encontrado: {}", workflowId);
            return false;
        }

        boolean isOwner = workflow.getCreatedBy() != null && workflow.getCreatedBy().equals(userId);
        if (!isOwner) {
            log.warn("❌ Solo el owner puede eliminar el workflow");
        }
        return isOwner;
    }
}
