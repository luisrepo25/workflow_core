package com.workflow.demo.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.enums.ProcessStatus;

public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, ObjectId> {
    
    // Búsquedas básicas
    List<ProcessInstance> findByClienteId(ObjectId clienteId);

    List<ProcessInstance> findByEstado(ProcessStatus estado);

    List<ProcessInstance> findByWorkflowId(ObjectId workflowId);

    Optional<ProcessInstance> findByCodigo(String codigo);

    // Búsquedas para activity assignment (Fase 6)
    
    /**
     * Busca procesos que tengan actividades pendientes asignadas a un usuario específico
     * Útil para que el usuario vea su cola de trabajo
     */
    @Query("{ 'actividades': { $elemMatch: { 'estado': 'pendiente', 'usuarioId': ?0 } } }")
    List<ProcessInstance> findProcessesWithPendingActivitiesForUser(ObjectId usuarioId);

    /**
     * Busca procesos que tengan actividades pendientes asignadas a un departamento
     */
    @Query("{ 'actividades': { $elemMatch: { 'estado': 'pendiente', 'responsableTipo': 'departamento', 'departmentId': ?0 } } }")
    List<ProcessInstance> findProcessesWithPendingActivitiesByDepartment(ObjectId departmentId);

    /**
     * Busca procesos que tengan actividades pendientes para el cliente
     */
    @Query("{ 'clienteId': ?0, 'actividades': { $elemMatch: { 'estado': 'pendiente', 'responsableTipo': 'cliente' } } }")
    List<ProcessInstance> findProcessesWithPendingActivitiesForClient(ObjectId clienteId);

    /**
     * Busca todos los procesos en estado específico dentro de un workflow
     */
    List<ProcessInstance> findByWorkflowIdAndEstado(ObjectId workflowId, ProcessStatus estado);

    @Query("{ 'actividades.actividadId': ?0 }")
    Optional<ProcessInstance> findByActividadId(String actividadId);

    /**
     * Busca procesos activos (no finalizados) de un cliente
     */
    @Query("{ 'clienteId': ?0, 'estado': { $in: ['pendiente', 'en_proceso'] } }")
    List<ProcessInstance> findActiveProcessesByCliente(ObjectId clienteId);

    /**
     * Busca procesos activos dentro de un workflow
     */
    @Query("{ 'workflowId': ?0, 'estado': { $in: ['pendiente', 'en_proceso'] } }")
    List<ProcessInstance> findActiveProcessesByWorkflow(ObjectId workflowId);

    /**
     * Busca todos los procesos activos en el sistema
     */
    @Query("{ 'estado': { $in: ['pendiente', 'en_proceso', 'correccion'] } }")
    List<ProcessInstance> findActiveProcesses();

    /**
     * Cuenta procesos en estado específico
     */
    long countByEstado(ProcessStatus estado);

    /**
     * Conta procesos por cliente en estado específico
     */
    long countByClienteIdAndEstado(ObjectId clienteId, ProcessStatus estado);

}