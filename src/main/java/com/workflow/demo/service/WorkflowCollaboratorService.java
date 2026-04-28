package com.workflow.demo.service;

import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.entity.WorkflowCollaborator;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorRole;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorStatus;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.repository.UserRepository;
import com.workflow.demo.repository.WorkflowCollaboratorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestionar colaboradores de workflows.
 * Maneja invitaciones, aceptaciones y cambios de rol.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCollaboratorService {

    private final WorkflowCollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;

    /**
     * Invita a un usuario a ser colaborador de un workflow
     */
    public WorkflowCollaborator inviteUserByEmail(ObjectId workflowId, String email, ObjectId invitedBy, CollaboratorRole role) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("No existe un usuario registrado con el email " + email));

        ObjectId userId = user.getId();

        // Verificar que el usuario no sea ya colaborador
        if (collaboratorRepository.existsByWorkflowIdAndUserId(workflowId, userId)) {
            throw new IllegalArgumentException("El usuario ya es colaborador de este workflow");
        }

        WorkflowCollaborator collaborator = WorkflowCollaborator.builder()
            .workflowId(workflowId)
            .userId(userId)
            .role(role)
            .invitedBy(invitedBy)
            .status(CollaboratorStatus.PENDING)
            .invitedAt(Instant.now())
            .build();

        collaborator = collaboratorRepository.save(collaborator);
        log.info("👥 Usuario {} ({}) invitado como {} al workflow {}", userId, email, role.getDisplayName(), workflowId);
        return collaborator;
    }

    /**
     * Acepta una invitación de colaboración
     */
    public WorkflowCollaborator acceptInvitation(ObjectId collaboratorId, ObjectId userId) {
        WorkflowCollaborator collaborator = collaboratorRepository.findById(collaboratorId)
            .orElseThrow(() -> new IllegalArgumentException("Colaboración no encontrada"));

        if (!collaborator.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para aceptar esta invitación");
        }

        if (collaborator.getStatus() != CollaboratorStatus.PENDING) {
            throw new IllegalArgumentException("Esta invitación no está pendiente");
        }

        collaborator.setStatus(CollaboratorStatus.ACCEPTED);
        collaborator.setAcceptedAt(Instant.now());
        collaborator = collaboratorRepository.save(collaborator);
        
        log.info("✅ Usuario {} aceptó invitación al workflow {}", userId, collaborator.getWorkflowId());
        return collaborator;
    }

    /**
     * Rechaza una invitación de colaboración
     */
    public void rejectInvitation(ObjectId collaboratorId, ObjectId userId) {
        WorkflowCollaborator collaborator = collaboratorRepository.findById(collaboratorId)
            .orElseThrow(() -> new IllegalArgumentException("Colaboración no encontrada"));

        if (!collaborator.getUserId().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para rechazar esta invitación");
        }

        collaborator.setStatus(CollaboratorStatus.REJECTED);
        collaboratorRepository.save(collaborator);
        log.info("❌ Usuario {} rechazó invitación al workflow {}", userId, collaborator.getWorkflowId());
    }

    /**
     * Cambia el rol de un colaborador
     */
    public WorkflowCollaborator changeRole(ObjectId workflowId, ObjectId userId, CollaboratorRole newRole, ObjectId requestingUserId) {
        WorkflowCollaborator collaborator = collaboratorRepository.findByWorkflowIdAndUserId(workflowId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Colaborador no encontrado"));

        collaborator.setRole(newRole);
        collaborator = collaboratorRepository.save(collaborator);
        log.info("🔄 Rol del usuario {} cambió a {} en workflow {}", userId, newRole.getDisplayName(), workflowId);
        return collaborator;
    }

    /**
     * Remueve un colaborador de un workflow
     */
    public void removeCollaborator(ObjectId workflowId, ObjectId userId) {
        WorkflowCollaborator collaborator = collaboratorRepository.findByWorkflowIdAndUserId(workflowId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Colaborador no encontrado"));

        collaborator.setStatus(CollaboratorStatus.REMOVED);
        collaboratorRepository.save(collaborator);
        log.info("🗑️  Usuario {} removido del workflow {}", userId, workflowId);
    }

    /**
     * Obtiene todos los colaboradores de un workflow
     */
    public List<WorkflowCollaborator> getCollaboratorsByWorkflow(ObjectId workflowId) {
        return collaboratorRepository.findByWorkflowId(workflowId);
    }

    /**
     * Obtiene todos los colaboradores aceptados de un workflow
     */
    public List<WorkflowCollaborator> getAcceptedCollaboratorsByWorkflow(ObjectId workflowId) {
        return collaboratorRepository.findAcceptedCollaboratorsByWorkflowId(workflowId);
    }

    /**
     * Obtiene todos los diseñadores de un workflow
     */
    public List<WorkflowCollaborator> getDesignersByWorkflow(ObjectId workflowId) {
        return collaboratorRepository.findDesignersByWorkflowId(workflowId);
    }

    /**
     * Obtiene todos los workflows donde el usuario es colaborador
     */
    public List<WorkflowCollaborator> getCollaborationsByUser(ObjectId userId) {
        return collaboratorRepository.findByUserId(userId);
    }

    /**
     * Obtiene las invitaciones pendientes de un usuario
     */
    public List<WorkflowCollaborator> getPendingInvitationsByUser(ObjectId userId) {
        return collaboratorRepository.findByUserIdAndStatus(userId, CollaboratorStatus.PENDING);
    }

    /**
     * Verifica si un usuario es colaborador (aceptado) de un workflow
     */
    public boolean isAcceptedCollaborator(ObjectId workflowId, ObjectId userId) {
        return collaboratorRepository.findByWorkflowIdAndUserId(workflowId, userId)
            .map(c -> c.getStatus() == CollaboratorStatus.ACCEPTED)
            .orElse(false);
    }

    /**
     * Verifica si un usuario puede editar un workflow
     * (es propietario o diseñador aceptado)
     */
    public boolean canEditWorkflow(ObjectId workflowId, ObjectId userId, ObjectId ownerId) {
        // El propietario siempre puede editar
        if (userId.equals(ownerId)) {
            return true;
        }

        // Verificar si es diseñador aceptado
        return collaboratorRepository.findByWorkflowIdAndUserId(workflowId, userId)
            .map(c -> c.getRole() == CollaboratorRole.DESIGNER && c.getStatus() == CollaboratorStatus.ACCEPTED)
            .orElse(false);
    }
}
