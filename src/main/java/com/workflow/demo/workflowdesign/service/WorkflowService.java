package com.workflow.demo.workflowdesign.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.entity.WorkflowCollaborator.CollaboratorStatus;
import com.workflow.demo.domain.enums.WorkflowStatus;
import com.workflow.demo.exception.WorkflowNotFoundException;
import com.workflow.demo.repository.WorkflowRepository;
import com.workflow.demo.service.WorkflowCollaboratorService;
import com.workflow.demo.workflowdesign.dto.CreateWorkflowRequest;
import com.workflow.demo.workflowdesign.dto.SaveWorkflowDesignRequest;
import com.workflow.demo.workflowdesign.dto.UpdateWorkflowRequest;
import com.workflow.demo.workflowdesign.dto.WorkflowDetailResponse;
import com.workflow.demo.workflowdesign.dto.WorkflowListItemResponse;
import com.workflow.demo.workflowdesign.dto.WorkflowValidationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowDesignValidationService workflowDesignValidationService;
    private final WorkflowCollaboratorService collaboratorService;

    public List<WorkflowListItemResponse> listar(String estado, String nombre) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userIdValue = authentication != null ? authentication.getName() : null;
        if (userIdValue == null || userIdValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        ObjectId userId = toObjectId(userIdValue);

        List<Workflow> workflows = new ArrayList<>(workflowRepository.findByCreatedBy(userId));

        Set<ObjectId> collaboratorWorkflowIds = collaboratorService.getCollaborationsByUser(userId).stream()
            .filter(collaborator -> collaborator.getStatus() == CollaboratorStatus.ACCEPTED)
            .map(collaborator -> collaborator.getWorkflowId())
            .collect(Collectors.toSet());

        if (!collaboratorWorkflowIds.isEmpty()) {
            workflows.addAll(workflowRepository.findAllById(collaboratorWorkflowIds));
        }

        workflows = workflows.stream()
            .distinct()
            .filter(workflow -> matchesEstado(workflow, estado))
            .filter(workflow -> matchesNombre(workflow, nombre))
            .toList();

        return workflows.stream().map(WorkflowListItemResponse::from).toList();
    }

    public List<WorkflowListItemResponse> listarActivosParaTramites() {
        return workflowRepository.findByEstado(WorkflowStatus.activo).stream()
            .map(WorkflowListItemResponse::from)
            .toList();
    }

    public WorkflowDetailResponse obtenerPorId(String workflowId) {
        return WorkflowDetailResponse.from(getWorkflow(workflowId));
    }

    public WorkflowDetailResponse crear(CreateWorkflowRequest request) {
        workflowRepository.findByCodigo(request.getCodigo()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un workflow con codigo " + request.getCodigo());
        });

        // ✅ Obtener el usuario autenticado del contexto de Spring Security
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        Workflow workflow = new Workflow();
        workflow.setCodigo(request.getCodigo());
        workflow.setNombre(request.getNombre());
        workflow.setDescripcion(request.getDescripcion());
        workflow.setEstado(request.getEstado() != null ? request.getEstado() : WorkflowStatus.borrador);
        workflow.setLanes(List.of());
        workflow.setNodes(List.of());
        workflow.setEdges(List.of());
        
        // ✅ Setear createdBy desde el usuario autenticado
        workflow.setCreatedBy(new ObjectId(userId));
        
        log.info("📋 Workflow creado: {} por usuario {}", request.getCodigo(), userId);

        return WorkflowDetailResponse.from(workflowRepository.save(workflow));
    }

    public WorkflowDetailResponse actualizarMetadata(String workflowId, UpdateWorkflowRequest request) {
        Workflow workflow = getWorkflow(workflowId);

        if (request.getNombre() != null) {
            workflow.setNombre(request.getNombre());
        }
        if (request.getDescripcion() != null) {
            workflow.setDescripcion(request.getDescripcion());
        }
        if (request.getEstado() != null) {
            workflow.setEstado(request.getEstado());
        }

        return WorkflowDetailResponse.from(workflowRepository.save(workflow));
    }

    public WorkflowDetailResponse guardarDiseno(String workflowId, SaveWorkflowDesignRequest request) {
        Workflow workflow = getWorkflow(workflowId);

        List<Lane> lanes = request.getLanes() != null ? new ArrayList<>(request.getLanes()) : new ArrayList<>();
        List<WorkflowNode> nodes = request.getNodes() != null ? new ArrayList<>(request.getNodes()) : new ArrayList<>();
        List<WorkflowEdge> edges = request.getEdges() != null ? new ArrayList<>(request.getEdges()) : new ArrayList<>();

        Map<String, ObjectId> laneDepartmentMap = new HashMap<>();
        for (Lane lane : lanes) {
            if (lane.getDepartmentId() != null && (lane.getId() == null || lane.getId().isBlank())) {
                lane.setId(lane.getDepartmentId().toHexString());
            }

            if (lane.getId() != null && !lane.getId().isBlank() && lane.getDepartmentId() != null) {
                laneDepartmentMap.put(lane.getId(), lane.getDepartmentId());
            }
        }

        for (WorkflowNode node : nodes) {
            if (node.getLaneId() == null || node.getLaneId().isBlank()) {
                continue;
            }

            ObjectId departmentId = laneDepartmentMap.get(node.getLaneId());
            if (departmentId != null) {
                node.setDepartmentId(departmentId);
            }
        }

        workflow.setLanes(lanes);
        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        // En estado borrador se permite guardar disenos incompletos sin validaciones estrictas.
        if (workflow.getEstado() != WorkflowStatus.borrador) {
            WorkflowValidationResponse validation = workflowDesignValidationService.validate(workflow);
            if (!validation.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Diseno invalido: " + String.join(" | ", validation.getErrors()));
            }
        }

        return WorkflowDetailResponse.from(workflowRepository.save(workflow));
    }

    public WorkflowValidationResponse validar(String workflowId) {
        Workflow workflow = getWorkflow(workflowId);
        return workflowDesignValidationService.validate(workflow);
    }

    public WorkflowDetailResponse activar(String workflowId) {
        Workflow workflow = getWorkflow(workflowId);
        WorkflowValidationResponse validation = workflowDesignValidationService.validate(workflow);
        if (!validation.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se puede activar. Errores: " + String.join(" | ", validation.getErrors()));
        }

        workflow.setEstado(WorkflowStatus.activo);
        return WorkflowDetailResponse.from(workflowRepository.save(workflow));
    }

    public WorkflowDetailResponse desactivar(String workflowId) {
        Workflow workflow = getWorkflow(workflowId);
        workflow.setEstado(WorkflowStatus.inactivo);
        return WorkflowDetailResponse.from(workflowRepository.save(workflow));
    }

    public WorkflowDetailResponse inactivarPorDelete(String workflowId) {
        return desactivar(workflowId);
    }

    private Workflow getWorkflow(String workflowId) {
        return workflowRepository
            .findById(toObjectId(workflowId))
            .orElseThrow(() -> new WorkflowNotFoundException("Workflow no encontrado: " + workflowId));
    }

    private boolean matchesEstado(Workflow workflow, String estado) {
        if (estado == null || estado.isBlank()) {
            return true;
        }

        try {
            WorkflowStatus status = WorkflowStatus.valueOf(estado);
            return workflow.getEstado() == status;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado invalido: " + estado);
        }
    }

    private boolean matchesNombre(Workflow workflow, String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return true;
        }

        String search = nombre.toLowerCase();
        return workflow.getNombre() != null && workflow.getNombre().toLowerCase().contains(search);
    }

    private ObjectId toObjectId(String value) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ObjectId invalido: " + value);
        }
    }
}