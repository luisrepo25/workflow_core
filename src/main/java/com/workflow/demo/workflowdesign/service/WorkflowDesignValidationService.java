package com.workflow.demo.workflowdesign.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.embedded.FormField;
import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.NodeType;
import com.workflow.demo.domain.enums.ResponsableTipo;
import com.workflow.demo.workflowdesign.dto.WorkflowValidationResponse;

@Service
public class WorkflowDesignValidationService {

    public WorkflowValidationResponse validate(Workflow workflow) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<Lane> lanes = workflow.getLanes() == null ? List.of() : workflow.getLanes();
        List<WorkflowNode> nodes = workflow.getNodes() == null ? List.of() : workflow.getNodes();
        List<WorkflowEdge> edges = workflow.getEdges() == null ? List.of() : workflow.getEdges();

        Map<String, ObjectId> laneDepartmentMap = validateLanes(lanes, errors);

        if (!nodes.isEmpty() && lanes.isEmpty()) {
            errors.add("El workflow tiene nodos pero no tiene lanes/departamentos");
        }

        if (nodes.stream().noneMatch(n -> n.getTipo() == NodeType.inicio)) {
            errors.add("El workflow debe tener un nodo inicio");
        }

        if (nodes.stream().noneMatch(n -> n.getTipo() == NodeType.fin)) {
            errors.add("El workflow debe tener al menos un nodo fin");
        }

        Set<String> nodeIds = nodes.stream().map(WorkflowNode::getId).collect(Collectors.toSet());
        if (nodeIds.contains(null) || nodeIds.contains("")) {
            errors.add("Todos los nodos deben tener id");
        }

        if (nodeIds.size() != nodes.size()) {
            errors.add("Existen nodos con id duplicado");
        }

        for (WorkflowEdge edge : edges) {
            if (!nodeIds.contains(edge.getFromNodeId()) || !nodeIds.contains(edge.getToNodeId())) {
                errors.add("Edge invalido: " + edge.getFromNodeId() + " -> " + edge.getToNodeId());
            }
        }

        for (WorkflowNode node : nodes) {
            if (node.getLaneId() == null || node.getLaneId().isBlank()) {
                errors.add("El nodo " + node.getId() + " no tiene laneId");
            } else if (!laneDepartmentMap.containsKey(node.getLaneId())) {
                errors.add("El nodo " + node.getId() + " referencia lane inexistente: " + node.getLaneId());
            } else {
                ObjectId laneDepartmentId = laneDepartmentMap.get(node.getLaneId());
                if (node.getDepartmentId() == null) {
                    errors.add("El nodo " + node.getId() + " no tiene departmentId");
                } else if (!laneDepartmentId.equals(node.getDepartmentId())) {
                    errors.add("El nodo " + node.getId() + " tiene departmentId distinto al de su lane");
                }
            }

            if (node.getTipo() == NodeType.decision
                && (node.getDecisionRule() == null)) {
                errors.add("El nodo decision " + node.getId() + " no tiene decisionRule");
            }

            if (node.getTipo() == NodeType.paralelo_inicio) {
                long outCount = edges.stream().filter(e -> node.getId().equals(e.getFromNodeId())).count();
                if (outCount < 2) {
                    errors.add("El nodo paralelo_inicio " + node.getId() + " debe tener al menos dos salidas");
                }
            }

            if (node.getTipo() == NodeType.paralelo_fin) {
                long inCount = edges.stream().filter(e -> node.getId().equals(e.getToNodeId())).count();
                if (inCount < 2) {
                    errors.add("El nodo paralelo_fin " + node.getId() + " debe tener al menos dos entradas");
                }
            }

            if (node.getTipo() == NodeType.actividad && node.getResponsableTipo() == null) {
                errors.add("El nodo actividad " + node.getId() + " no tiene responsableTipo");
            }

            if (node.getTipo() == NodeType.actividad && node.getResponsableTipo() == ResponsableTipo.usuario
                && node.getResponsableUsuarioId() == null) {
                errors.add("El nodo actividad " + node.getId() + " de tipo usuario no tiene responsableUsuarioId");
            }

            if (node.getTipo() == NodeType.actividad && node.getResponsableTipo() == ResponsableTipo.departamento
                && node.getDepartmentId() == null) {
                errors.add("El nodo actividad " + node.getId() + " de tipo departamento no tiene departmentId");
            }

            if (node.getTipo() == NodeType.actividad && node.getSlaMinutos() == null) {
                warnings.add("El nodo " + node.getId() + " no tiene SLA definido");
            }

            if (node.getForm() != null && node.getForm().getCampos() != null) {
                validateFormFields(node.getId(), node.getForm().getCampos(), errors);
            }
        }

        return WorkflowValidationResponse.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .warnings(warnings)
            .build();
    }

    private Map<String, ObjectId> validateLanes(List<Lane> lanes, List<String> errors) {
        Map<String, ObjectId> laneDepartmentMap = new HashMap<>();
        Set<String> laneIds = new HashSet<>();
        Set<ObjectId> departmentIds = new HashSet<>();

        for (Lane lane : lanes) {
            if (lane.getId() == null || lane.getId().isBlank()) {
                errors.add("Todas las lanes deben tener id");
                continue;
            }

            if (lane.getDepartmentId() == null) {
                errors.add("La lane " + lane.getId() + " no tiene departmentId");
                continue;
            }

            if (!laneIds.add(lane.getId())) {
                errors.add("Existen lanes con id duplicado");
            }

            if (!departmentIds.add(lane.getDepartmentId())) {
                errors.add("Un departamento no puede estar repetido en varias lanes");
            }

            laneDepartmentMap.put(lane.getId(), lane.getDepartmentId());
        }

        return laneDepartmentMap;
    }

    private void validateFormFields(String nodeId, List<FormField> fields, List<String> errors) {
        Set<String> ids = new HashSet<>();
        for (FormField field : fields) {
            if (field.getId() == null || field.getId().isBlank()) {
                errors.add("Campo de formulario sin id en nodo " + nodeId);
            }
            if (field.getLabel() == null || field.getLabel().isBlank()) {
                errors.add("Campo de formulario sin label en nodo " + nodeId);
            }
            if (field.getTipo() == null) {
                errors.add("Campo de formulario sin tipo en nodo " + nodeId);
            }
            if (field.getId() != null && !ids.add(field.getId())) {
                errors.add("Campo de formulario duplicado " + field.getId() + " en nodo " + nodeId);
            }
        }
    }
}