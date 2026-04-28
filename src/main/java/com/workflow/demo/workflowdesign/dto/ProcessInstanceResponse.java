package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.embedded.WorkflowSnapshot;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.enums.ProcessStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para un ProcessInstance (trámite).
 * Las actividades incluyen el formulario del nodo tomado del WorkflowSnapshot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessInstanceResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("codigo")
    private String codigo;

    @JsonProperty("workflowId")
    private String workflowId;

    @JsonProperty("clienteId")
    private String clienteId;

    @JsonProperty("estado")
    private ProcessStatus estado;

    @JsonProperty("currentNodeIds")
    private List<String> currentNodeIds;

    @JsonProperty("actividades")
    private List<ActivityResponseDto> actividades;

        @JsonProperty("workflowSnapshot")
        private WorkflowSnapshot workflowSnapshot;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("finishedAt")
    private Instant finishedAt;

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Mapeo completo: todas las actividades enriquecidas con el formulario del nodo
     * usando el WorkflowSnapshot embebido en el propio ProcessInstance.
     */
        public static ProcessInstanceResponse from(ProcessInstance instance) {
                return from(instance, false);
        }

        /**
         * Mapeo completo con snapshot del workflow incluido.
         * Usa este formato para detalle de un trámite en móvil.
         */
        public static ProcessInstanceResponse fromDetailed(ProcessInstance instance) {
                return from(instance, true);
        }

        private static ProcessInstanceResponse from(ProcessInstance instance, boolean includeSnapshot) {
        if (instance == null) return null;

        WorkflowSnapshot snapshot = instance.getWorkflowSnapshot();

                return ProcessInstanceResponse.builder()
                .id(instance.getId().toHexString())
                .codigo(instance.getCodigo())
                .workflowId(instance.getWorkflowId().toHexString())
                .clienteId(instance.getClienteId().toHexString())
                .estado(instance.getEstado())
                .currentNodeIds(instance.getCurrentNodeIds())
                .actividades(instance.getActividades() != null
                        ? instance.getActividades().stream()
                                .map(a -> ActivityResponseDto.from(a, resolveNode(snapshot, a.getNodeId())))
                                .toList()
                        : List.of())
                                .workflowSnapshot(includeSnapshot ? snapshot : null)
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .finishedAt(instance.getFinishedAt())
                .build();
    }

    /**
     * Mapeo con filtro de actividades por ID (usado en /activities/pending).
     * Solo incluye las actividades cuyo actividadId esté en el conjunto indicado.
     * Todas las actividades incluidas llevan el formulario del nodo desde el snapshot.
     */
    public static ProcessInstanceResponse from(ProcessInstance instance, Set<String> activityIdsFilter) {
        if (instance == null) return null;

        WorkflowSnapshot snapshot = instance.getWorkflowSnapshot();

        return ProcessInstanceResponse.builder()
                .id(instance.getId().toHexString())
                .codigo(instance.getCodigo())
                .workflowId(instance.getWorkflowId().toHexString())
                .clienteId(instance.getClienteId().toHexString())
                .estado(instance.getEstado())
                .currentNodeIds(instance.getCurrentNodeIds())
                .actividades(instance.getActividades() != null
                        ? instance.getActividades().stream()
                                .filter(a -> a != null
                                        && (activityIdsFilter == null || activityIdsFilter.isEmpty()
                                                || activityIdsFilter.contains(a.getActividadId())))
                                .map(a -> ActivityResponseDto.from(a, resolveNode(snapshot, a.getNodeId())))
                                .toList()
                        : List.of())
                .workflowSnapshot(null)
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .finishedAt(instance.getFinishedAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Busca el WorkflowNode en el snapshot por su ID.
     * Si el snapshot es null o no contiene el nodo devuelve null
     * (ActivityResponseDto.from tolera node=null).
     */
    private static WorkflowNode resolveNode(WorkflowSnapshot snapshot, String nodeId) {
        if (snapshot == null || nodeId == null) return null;
        return snapshot.getNodeById(nodeId);
    }
}
