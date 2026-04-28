package com.workflow.demo.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.workflow.demo.domain.entity.WorkflowCollaborator;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorStatus;

/**
 * Repositorio para gestionar colaboradores de workflows.
 */
@Repository
public interface WorkflowCollaboratorRepository extends MongoRepository<WorkflowCollaborator, ObjectId> {
    
    /**
     * Obtiene todos los colaboradores de un workflow
     */
    List<WorkflowCollaborator> findByWorkflowId(ObjectId workflowId);
    
    /**
     * Obtiene un colaborador específico en un workflow
     */
    Optional<WorkflowCollaborator> findByWorkflowIdAndUserId(ObjectId workflowId, ObjectId userId);
    
    /**
     * Obtiene todos los workflows donde el usuario es colaborador
     */
    List<WorkflowCollaborator> findByUserId(ObjectId userId);
    
    /**
     * Obtiene todos los colaboradores aceptados de un workflow
     */
    @Query("{ 'workflowId': ?0, 'status': 'ACCEPTED' }")
    List<WorkflowCollaborator> findAcceptedCollaboratorsByWorkflowId(ObjectId workflowId);

    /**
     * Obtiene las invitaciones pendientes de un usuario
     */
    List<WorkflowCollaborator> findByUserIdAndStatus(ObjectId userId, CollaboratorStatus status);
    
    /**
     * Obtiene todos los diseñadores (DESIGNER) en un workflow
     */
    @Query("{ 'workflowId': ?0, 'role': 'DESIGNER' }")
    List<WorkflowCollaborator> findDesignersByWorkflowId(ObjectId workflowId);
    
    /**
     * Elimina todos los colaboradores de un workflow
     */
    void deleteByWorkflowId(ObjectId workflowId);
    
    /**
     * Verifica si un usuario es colaborador de un workflow
     */
    boolean existsByWorkflowIdAndUserId(ObjectId workflowId, ObjectId userId);
}
