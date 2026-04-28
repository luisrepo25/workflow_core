package com.workflow.demo.workflowdesign.controller;

import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.service.WorkflowEngineService;
import com.workflow.demo.workflowdesign.dto.CompleteActivityRequest;
import com.workflow.demo.workflowdesign.dto.CreateProcessInstanceRequest;
import com.workflow.demo.workflowdesign.dto.ProcessInstanceResponse;
import com.workflow.demo.workflowdesign.service.ActivityFileUploadService;
import com.workflow.demo.workflowdesign.service.ProcessInstanceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para gestión de instancias de procesos (trámites)
 */
@Slf4j
@RestController
@RequestMapping("/api/process-instances")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;
    private final WorkflowEngineService workflowEngineService;
    private final com.workflow.demo.repository.ProcessInstanceRepository processInstanceRepository;
    private final ActivityFileUploadService activityFileUploadService;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/process-instances
     * Crea una nueva instancia de proceso (trámite)
         * Roles: FUNCIONARIO, DESIGNER, CLIENT, ADMIN
     */
    @PostMapping
        @PreAuthorize("hasAnyRole('FUNCIONARIO', 'DESIGNER', 'CLIENT', 'ADMIN')")
    public ResponseEntity<ProcessInstanceResponse> createProcessInstance(
            @Valid @RequestBody CreateProcessInstanceRequest request,
            Authentication authentication) {
        
        ObjectId userId = new ObjectId(authentication.getName());
        log.info("📋 Creando trámite para workflow {} por usuario {}", request.getWorkflowId(), userId.toHexString());
        
        ProcessInstanceResponse response = processInstanceService.createProcessInstance(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/process-instances
     * Obtiene todas las instancias del usuario autenticado
     * Roles: CLIENT, FUNCIONARIO
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<List<ProcessInstanceResponse>> getMyProcessInstances(Authentication authentication) {
        ObjectId userId = new ObjectId(authentication.getName());
        log.info("📋 Listando trámites para cliente {}", userId.toHexString());
        
        List<ProcessInstanceResponse> instances = processInstanceService.getProcessInstancesByClient(userId);
        return ResponseEntity.ok(instances);
    }

    /**
     * GET /api/process-instances/all
     * Obtiene todas las instancias de trámite con información completa
     * Roles: FUNCIONARIO, DESIGNER, ADMIN
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'DESIGNER', 'ADMIN')")
    public ResponseEntity<List<ProcessInstanceResponse>> getAllProcessInstances() {
        log.info("📋 Listando todos los trámites");

        List<ProcessInstanceResponse> instances = processInstanceService.getAllProcessInstances();
        return ResponseEntity.ok(instances);
    }

    /**
     * GET /api/process-instances/{processInstanceId}
     * Obtiene los detalles de una instancia específica
     * Roles: CLIENT, FUNCIONARIO, DESIGNER
     */
    @GetMapping("/{processInstanceId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FUNCIONARIO', 'DESIGNER', 'ADMIN')")
    public ResponseEntity<ProcessInstanceResponse> getProcessInstance(
            @PathVariable String processInstanceId,
            Authentication authentication) {
        
        ObjectId userId = new ObjectId(authentication.getName());
        ObjectId instanceId = new ObjectId(processInstanceId);
        
        log.info("📋 Obteniendo detalle de trámite {} para usuario {}", processInstanceId, userId.toHexString());
        
        ProcessInstanceResponse response = processInstanceService.getProcessInstanceById(instanceId);
        
        // TODO: Validar que el usuario tenga acceso a este trámite
        // - Si es CLIENT, debe ser el clienteId
        // - Si es FUNCIONARIO, debe tener actividades asignadas
        // - Si es DESIGNER, puede acceder al suyo
        
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/process-instances/{processInstanceId}/history
     * Obtiene el historial de cambios de una instancia
     */
    @GetMapping("/{processInstanceId}/history")
    @PreAuthorize("hasAnyRole('CLIENT', 'FUNCIONARIO', 'DESIGNER', 'ADMIN')")
    public ResponseEntity<Object> getProcessInstanceHistory(@PathVariable String processInstanceId) {
        ObjectId instanceId = new ObjectId(processInstanceId);
        Object history = processInstanceService.getProcessInstanceHistory(instanceId);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/activities/pending
     * Obtiene todas las actividades pendientes para el usuario (Funcionario)
     * Roles: FUNCIONARIO
     */
    @GetMapping("/activities/pending")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<List<ProcessInstanceResponse>> getPendingActivities(Authentication authentication) {
        ObjectId userId = new ObjectId(authentication.getName());
        log.info("📋 Listando actividades pendientes para funcionario {}", userId.toHexString());
        
        List<ProcessInstanceResponse> activities = processInstanceService.getPendingActivitiesForUser(userId);
        return ResponseEntity.ok(activities);
    }

    /**
     * POST /api/activities/{actividadId}/complete
     * Completa una actividad específica
     * Roles: FUNCIONARIO
     */
    @PostMapping(value = "/activities/{actividadId}/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<ProcessInstanceResponse> completeActivity(
            @PathVariable String actividadId,
            @Valid @RequestBody CompleteActivityRequest request,
            Authentication authentication) {
        
        ObjectId userId = new ObjectId(authentication.getName());
        log.info("✅ Completando actividad {} por usuario {}", actividadId, userId.toHexString());
        
        try {
            ProcessInstance updatedInstance = completeActivityInternal(actividadId, userId, request.getRespuestaFormulario());
            return ResponseEntity.ok(ProcessInstanceResponse.from(updatedInstance));
            
        } catch (Exception e) {
            log.error("❌ Error al completar actividad: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/activities/{actividadId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMIN')")
    public ResponseEntity<ProcessInstanceResponse> completeActivityMultipart(
            @PathVariable String actividadId,
            @RequestParam("payload") String payload,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "fileFieldIds", required = false) List<String> fileFieldIds,
            Authentication authentication) {

        ObjectId userId = new ObjectId(authentication.getName());
        log.info("✅ Completando actividad multipart {} por usuario {}", actividadId, userId.toHexString());

        try {
            CompleteActivityRequest request = objectMapper.readValue(payload, CompleteActivityRequest.class);

            ProcessInstance instance = processInstanceRepository.findByActividadId(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el tramite con la actividad " + actividadId));

            String nodeId = extractNodeId(instance, actividadId);

            Map<String, Object> respuestaFormulario = request.getRespuestaFormulario();
            Map<String, Object> enriched = activityFileUploadService.injectUploadedFilesIntoRespuesta(
                instance,
                actividadId,
                nodeId,
                userId,
                respuestaFormulario,
                files,
                fileFieldIds
            );

            ProcessInstance updatedInstance = completeActivityInternal(actividadId, userId, enriched);
            return ResponseEntity.ok(ProcessInstanceResponse.from(updatedInstance));
        } catch (Exception e) {
            log.error("❌ Error al completar actividad multipart: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private ProcessInstance completeActivityInternal(
            String actividadId,
            ObjectId userId,
            Map<String, Object> respuestaFormulario) {
        ProcessInstance instance = processInstanceRepository.findByActividadId(actividadId)
            .orElseThrow(() -> new IllegalArgumentException("No se encontro el tramite con la actividad " + actividadId));

        String nodeId = extractNodeId(instance, actividadId);

        return workflowEngineService.completarActividad(
            instance.getId().toHexString(),
            nodeId,
            userId.toHexString(),
            respuestaFormulario
        );
    }

    private String extractNodeId(ProcessInstance instance, String actividadId) {
        return instance.getActividades().stream()
            .filter(a -> actividadId.equals(a.getActividadId()))
            .map(ProcessActivity::getNodeId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No se encontro nodeId para la actividad " + actividadId));
    }
}
