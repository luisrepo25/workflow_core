package com.workflow.demo.workflowdesign.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.embedded.ProcessActivity;

import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.ProcessStatus;
import com.workflow.demo.domain.enums.WorkflowStatus;
import com.workflow.demo.repository.ProcessInstanceRepository;
import com.workflow.demo.repository.WorkflowRepository;
import com.workflow.demo.service.ActivityAssignmentService;
import com.workflow.demo.service.NotificationService;
import com.workflow.demo.service.WorkflowEngineService;
import com.workflow.demo.workflowdesign.dto.CreateProcessInstanceRequest;
import com.workflow.demo.workflowdesign.dto.ProcessInstanceResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestionar instancias de procesos (trámites)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowEngineService workflowEngineService;
    private final ActivityAssignmentService activityAssignmentService;
    private final NotificationService notificationService;

    /**
     * Crea una nueva instancia de proceso (trámite)
     * Valida que el workflow esté activo y obtiene el nodo inicial para comenzar
     * También captura un snapshot del workflow (Fase 8 - Versionado)
     */
    public ProcessInstanceResponse createProcessInstance(CreateProcessInstanceRequest request, ObjectId createdByUserId) {
        // Validar que el workflow exista y esté activo
        Workflow workflow = workflowRepository.findById(new ObjectId(request.getWorkflowId()))
            .orElseThrow(() -> new IllegalArgumentException("Workflow no encontrado"));

        if (workflow.getEstado() != WorkflowStatus.activo) {
            throw new IllegalArgumentException("El workflow debe estar activo para crear instancias. Estado actual: " + workflow.getEstado());
        }

        // Crear la instancia
        ProcessInstance instance = new ProcessInstance();
        instance.setWorkflowId(workflow.getId());
        instance.setClienteId(new ObjectId(request.getClienteId()));
        instance.setEstado(ProcessStatus.pendiente);
        
        // Generar código único para el trámite (Fase 8)
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(7);
        instance.setCodigo("TRM-" + workflow.getCodigo() + "-" + timestamp);
        instance.setCurrentNodeIds(new ArrayList<>());
        instance.setActividades(new ArrayList<>());
        instance.setHistorial(new ArrayList<>());
        instance.setCreatedAt(Instant.now());
        
        // Capturar snapshot del workflow (Fase 8 - Versionado)
        instance.setWorkflowSnapshot(com.workflow.demo.domain.embedded.WorkflowSnapshot.from(workflow, createdByUserId));
        log.info("📸 Snapshot del workflow {} capturado para trámite", workflow.getId().toHexString());

        instance = processInstanceRepository.save(instance);
        log.info("✅ Trámite creado: {} para workflow {}", instance.getId().toHexString(), workflow.getId().toHexString());

        // Iniciar el flujo de trabajo desde el primer nodo
        try {
            workflowEngineService.initializeWorkflow(instance.getId().toHexString(), request.getDatosIniciales(), createdByUserId);
        } catch (Exception e) {
            log.error("❌ Error al inicializar workflow: {}", e.getMessage());
            throw new RuntimeException("Error al inicializar el workflow", e);
        }

        // Obtener instancia actualizada
        instance = processInstanceRepository.findById(instance.getId()).orElse(instance);
        notificationService.notificarTramiteCreado(instance);
        return ProcessInstanceResponse.from(instance);
    }

    /**
     * Obtiene todas las instancias de un cliente
     */
    public List<ProcessInstanceResponse> getProcessInstancesByClient(ObjectId clienteId) {
        List<ProcessInstance> instances = processInstanceRepository.findByClienteId(clienteId);
        return instances.stream()
            .map(ProcessInstanceResponse::from)
            .toList();
    }

    /**
     * Obtiene todas las instancias de trámite con información completa
     */
    public List<ProcessInstanceResponse> getAllProcessInstances() {
        List<ProcessInstance> instances = processInstanceRepository.findAll();
        return instances.stream()
            .map(ProcessInstanceResponse::from)
            .toList();
    }

    /**
     * Obtiene una instancia específica
     */
    public ProcessInstanceResponse getProcessInstanceById(ObjectId processInstanceId) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        return ProcessInstanceResponse.fromDetailed(instance);
    }

    /**
     * Obtiene todas las actividades pendientes para un funcionario
     * Usa ActivityAssignmentService para gestionar la lógica
     */
    public List<ProcessInstanceResponse> getPendingActivitiesForUser(ObjectId usuarioId) {
        log.debug("🔍 Buscando actividades pendientes para usuario: {}", usuarioId);
        
        // El nuevo método nos devuelve el mapa Actividad -> Instancia (con correcciones en memoria aplicadas)
        Map<ProcessActivity, ProcessInstance> pendingInfo = activityAssignmentService.getPendingActivitiesWithInstancesForUser(usuarioId);
        log.info("✅ Usuario {} tiene {} actividades pendientes", usuarioId, pendingInfo.size());

        Map<String, ProcessInstance> instancesById = new LinkedHashMap<>();
        Map<String, Set<String>> pendingActivityIdsByInstance = new LinkedHashMap<>();

        for (Map.Entry<ProcessActivity, ProcessInstance> entry : pendingInfo.entrySet()) {
            ProcessActivity activity = entry.getKey();
            ProcessInstance instance = entry.getValue();

            if (activity == null || instance == null) continue;

            String instanceId = instance.getId().toHexString();
            instancesById.putIfAbsent(instanceId, instance);
            pendingActivityIdsByInstance
                .computeIfAbsent(instanceId, key -> new java.util.LinkedHashSet<>())
                .add(activity.getActividadId());
        }

        return instancesById.entrySet().stream()
            .map(entry -> ProcessInstanceResponse.from(
                entry.getValue(),
                pendingActivityIdsByInstance.getOrDefault(entry.getKey(), Set.of())))
            .toList();
    }

    /**
     * Obtiene actividades pendientes de un cliente
     */
    public List<ProcessActivity> getPendingActivitiesForClient(ObjectId clienteId) {
        log.debug("🔍 Buscando actividades pendientes para cliente: {}", clienteId);
        return activityAssignmentService.getPendingActivitiesForClient(clienteId);
    }

    /**
     * Obtiene actividades pendientes de un departamento
     */
    public List<ProcessActivity> getPendingActivitiesByDepartment(ObjectId departmentId) {
        log.debug("🔍 Buscando actividades pendientes para departamento: {}", departmentId);
        return activityAssignmentService.getPendingActivitiesByDepartment(departmentId);
    }

    /**
     * Valida que un usuario puede ejecutar una actividad
     */
    public boolean canUserExecuteActivity(ObjectId processInstanceId, String actividadId, ObjectId usuarioId) {
        log.debug("🔐 Validando permisos: usuario {} para actividad {}", usuarioId, actividadId);
        return activityAssignmentService.canUserExecuteActivity(processInstanceId, actividadId, usuarioId);
    }

    /**
     * Reasigna una actividad a otro usuario
     */
    public boolean reassignActivity(ObjectId processInstanceId, String actividadId, ObjectId newUsuarioId) {
        log.info("🔄 Reasignando actividad {} a usuario {}", actividadId, newUsuarioId);
        return activityAssignmentService.reassignActivity(processInstanceId, actividadId, newUsuarioId);
    }

    /**
     * Obtiene el historial de un trámite
     */
    public Object getProcessInstanceHistory(ObjectId processInstanceId) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        
        return Map.of(
            "processInstanceId", instance.getId().toHexString(),
            "historial", instance.getHistorial() != null ? instance.getHistorial() : new ArrayList<>()
        );
    }
}
