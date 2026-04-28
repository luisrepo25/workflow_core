package com.workflow.demo.workflowdesign.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.workflow.demo.domain.embedded.DecisionRule;
import com.workflow.demo.domain.embedded.FormField;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.FieldType;
import com.workflow.demo.domain.enums.NodeType;
import com.workflow.demo.domain.enums.WorkflowStatus;
import com.workflow.demo.domain.enums.ConditionOperator;

import com.workflow.demo.repository.WorkflowRepository;
import com.workflow.demo.service.ConditionEvaluationService;
import com.workflow.demo.workflowdesign.dto.DecisionContextResponse;
import com.workflow.demo.workflowdesign.dto.DecisionContextResponse.DecisionContextField;
import com.workflow.demo.workflowdesign.dto.DecisionRulesPatchResponse;
import com.workflow.demo.workflowdesign.dto.DecisionRulesSimulateResponse;
import com.workflow.demo.workflowdesign.dto.DecisionRulesValidationResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DecisionRuleService {

    private final WorkflowRepository workflowRepository;
    private final ConditionEvaluationService conditionEvaluationService;

    public DecisionContextResponse getDecisionContext(String workflowId, String decisionNodeId) {
        Workflow workflow = getWorkflow(workflowId);
        WorkflowNode decisionNode = getDecisionNode(workflow, decisionNodeId);

        List<String> incomingNodeIds = workflow.getEdges() == null
            ? new ArrayList<>()
            : workflow.getEdges().stream()
                .filter(edge -> decisionNode.getId().equals(edge.getToNodeId()))
                .map(WorkflowEdge::getFromNodeId)
                .distinct()
                .toList();

        List<DecisionContextField> fields = new ArrayList<>();
        for (String sourceNodeId : incomingNodeIds) {
            WorkflowNode sourceNode = findNode(workflow, sourceNodeId);
            if (sourceNode == null || sourceNode.getForm() == null || sourceNode.getForm().getCampos() == null) {
                continue;
            }
            for (FormField field : sourceNode.getForm().getCampos()) {
                fields.add(DecisionContextField.builder()
                    .fieldId(field.getId())
                    .label(field.getLabel())
                    .type(normalizeFieldType(field.getTipo()))
                    .sourceNodeId(sourceNode.getId())
                    .build());
            }
        }

        Map<String, List<String>> operatorsByType = new LinkedHashMap<>();
        operatorsByType.put("bool", List.of(
            ConditionOperator.EQUALS.getSymbol(),
            ConditionOperator.NOT_EQUALS.getSymbol()
        ));
        operatorsByType.put("number", List.of(
            ConditionOperator.GREATER_THAN.getSymbol(),
            ConditionOperator.GREATER_EQUAL_THAN.getSymbol(),
            ConditionOperator.LESS_THAN.getSymbol(),
            ConditionOperator.LESS_EQUAL_THAN.getSymbol(),
            ConditionOperator.EQUALS.getSymbol(),
            ConditionOperator.NOT_EQUALS.getSymbol()
        ));
        operatorsByType.put("text", List.of(
            ConditionOperator.EQUALS.getSymbol(),
            ConditionOperator.NOT_EQUALS.getSymbol(),
            ConditionOperator.CONTAINS.getSymbol(),
            ConditionOperator.STARTS_WITH.getSymbol(),
            ConditionOperator.ENDS_WITH.getSymbol()
        ));
        operatorsByType.put("date", List.of(
            ConditionOperator.GREATER_THAN.getSymbol(),
            ConditionOperator.GREATER_EQUAL_THAN.getSymbol(),
            ConditionOperator.LESS_THAN.getSymbol(),
            ConditionOperator.LESS_EQUAL_THAN.getSymbol(),
            ConditionOperator.EQUALS.getSymbol(),
            ConditionOperator.NOT_EQUALS.getSymbol()
        ));

        return DecisionContextResponse.builder()
            .decisionNodeId(decisionNode.getId())
            .incomingNodeIds(incomingNodeIds)
            .fields(fields)
            .operatorsByType(operatorsByType)
            .build();
    }

    public DecisionRulesPatchResponse patchDecisionRules(
            String workflowId,
            String decisionNodeId,
            DecisionRule decisionRule,
            String mode) {
        Workflow workflow = getWorkflow(workflowId);
        WorkflowNode decisionNode = getDecisionNode(workflow, decisionNodeId);

        DecisionRulesValidationResult validation = validateDecisionRulesInternal(workflow, decisionNode, decisionRule, mode);

        boolean isDraft = workflow.getEstado() == WorkflowStatus.borrador;
        if (!isDraft && !validation.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Reglas invalidas: " + String.join(" | ", validation.getErrors()));
        }

        decisionNode.setDecisionRule(decisionRule);
        Workflow saved = workflowRepository.save(workflow);

        Instant updatedAt = saved.getUpdatedAt() != null ? saved.getUpdatedAt() : Instant.now();
        return DecisionRulesPatchResponse.of(decisionNodeId, updatedAt, decisionRule);
    }

    public DecisionRulesValidationResult validateDecisionRules(
            String workflowId,
            String decisionNodeId,
            DecisionRule decisionRule,
            String mode) {
        Workflow workflow = getWorkflow(workflowId);
        WorkflowNode decisionNode = getDecisionNode(workflow, decisionNodeId);

        return validateDecisionRulesInternal(workflow, decisionNode, decisionRule, mode);
    }

    public DecisionRulesSimulateResponse simulateDecisionRules(
            String workflowId,
            String decisionNodeId,
            Map<String, Object> input,
            DecisionRule decisionRule,
            String mode) {
        Workflow workflow = getWorkflow(workflowId);
        WorkflowNode decisionNode = getDecisionNode(workflow, decisionNodeId);

        DecisionRule ruleToSimulate = decisionRule != null ? decisionRule : decisionNode.getDecisionRule();

        DecisionRulesValidationResult validation = validateDecisionRulesInternal(workflow, decisionNode, ruleToSimulate, mode);
        if (!validation.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se puede simular. Reglas invalidas: " + String.join(" | ", validation.getErrors()));
        }

        Map<String, Object> safeInput = input == null ? new HashMap<>() : input;
        List<String> trace = new ArrayList<>();

        if (ruleToSimulate == null || ruleToSimulate.getField() == null || ruleToSimulate.getField().isBlank()) {
            trace.add("Sin regla configurada => false destination");
            return DecisionRulesSimulateResponse.of(false, "Sin regla", null, trace);
        }

        boolean result = conditionEvaluationService.evaluateRule(ruleToSimulate, safeInput);
        String operatorSymbol = ruleToSimulate.getOperator() != null ? ruleToSimulate.getOperator().getSymbol() : "?";
        String conditionText = ruleToSimulate.getField() + " " + operatorSymbol + " " + ruleToSimulate.getValue();
        trace.add(conditionText + " => " + result);

        String targetNodeId = result ? ruleToSimulate.getOnTrueDestinoNodeId() : ruleToSimulate.getOnFalseDestinoNodeId();

        return DecisionRulesSimulateResponse.of(true, "Regla principal", targetNodeId, trace);
    }

    private DecisionRulesValidationResult validateDecisionRulesInternal(
            Workflow workflow,
            WorkflowNode decisionNode,
            DecisionRule rule,
            String mode) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (rule == null) {
            errors.add("El nodo decision debe tener una regla configurada (decisionRule)");
            return DecisionRulesValidationResult.of(errors, warnings);
        }

        Set<String> availableFields = getAvailableFieldIdsForDecision(workflow, decisionNode.getId());
        Set<String> validDestinations = getOutgoingDestinations(workflow, decisionNode.getId());

        if (rule.getOnTrueDestinoNodeId() == null || rule.getOnTrueDestinoNodeId().isBlank()) {
            errors.add("onTrueDestinoNodeId es requerido");
        } else if (!existsNode(workflow, rule.getOnTrueDestinoNodeId())) {
            errors.add("onTrueDestinoNodeId no existe en workflow: " + rule.getOnTrueDestinoNodeId());
        } else if (!validDestinations.contains(rule.getOnTrueDestinoNodeId())) {
            errors.add("onTrueDestinoNodeId debe estar conectado por edge desde el nodo decision");
        }

        if (rule.getOnFalseDestinoNodeId() == null || rule.getOnFalseDestinoNodeId().isBlank()) {
            errors.add("onFalseDestinoNodeId es requerido");
        } else if (!existsNode(workflow, rule.getOnFalseDestinoNodeId())) {
            errors.add("onFalseDestinoNodeId no existe en workflow: " + rule.getOnFalseDestinoNodeId());
        } else if (!validDestinations.contains(rule.getOnFalseDestinoNodeId())) {
            errors.add("onFalseDestinoNodeId debe estar conectado por edge desde el nodo decision");
        }

        String fieldId = rule.getField();
        if (fieldId == null || fieldId.isBlank()) {
            errors.add("El campo a evaluar es requerido");
            return DecisionRulesValidationResult.of(errors, warnings);
        }

        if (rule.getOperator() == null) {
            errors.add("operador es requerido");
        }
        if (rule.getValue() == null) {
            errors.add("valor es requerido");
        }

        if (!availableFields.contains(fieldId)) {
            errors.add("el campo no esta disponible desde nodos anteriores: " + fieldId);
        }

        return DecisionRulesValidationResult.of(errors, warnings);
    }

    private Set<String> getOutgoingDestinations(Workflow workflow, String decisionNodeId) {
        Set<String> destinations = new LinkedHashSet<>();
        List<WorkflowEdge> edges = workflow.getEdges() == null ? new ArrayList<>() : workflow.getEdges();
        for (WorkflowEdge edge : edges) {
            if (edge != null && decisionNodeId.equals(edge.getFromNodeId()) && edge.getToNodeId() != null) {
                destinations.add(edge.getToNodeId());
            }
        }
        return destinations;
    }

    private Set<String> getAvailableFieldIdsForDecision(Workflow workflow, String decisionNodeId) {
        Set<String> fieldIds = new LinkedHashSet<>();
        List<WorkflowEdge> edges = workflow.getEdges() == null ? new ArrayList<>() : workflow.getEdges();

        for (WorkflowEdge edge : edges) {
            if (edge == null || !decisionNodeId.equals(edge.getToNodeId())) {
                continue;
            }
            WorkflowNode sourceNode = findNode(workflow, edge.getFromNodeId());
            if (sourceNode == null || sourceNode.getForm() == null || sourceNode.getForm().getCampos() == null) {
                continue;
            }
            for (FormField field : sourceNode.getForm().getCampos()) {
                if (field.getId() != null && !field.getId().isBlank()) {
                    fieldIds.add(field.getId());
                }
            }
        }

        return fieldIds;
    }

    private boolean existsNode(Workflow workflow, String nodeId) {
        return findNode(workflow, nodeId) != null;
    }

    private WorkflowNode findNode(Workflow workflow, String nodeId) {
        if (workflow.getNodes() == null) {
            return null;
        }

        for (WorkflowNode node : workflow.getNodes()) {
            if (node != null && nodeId.equals(node.getId())) {
                return node;
            }
        }

        return null;
    }

    private String normalizeFieldType(FieldType fieldType) {
        if (fieldType == null) {
            return "text";
        }

        return switch (fieldType) {
            case bool -> "bool";
            case number -> "number";
            case date -> "date";
            default -> "text";
        };
    }

    private Workflow getWorkflow(String workflowId) {
        return workflowRepository.findById(toObjectId(workflowId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Workflow no encontrado: " + workflowId));
    }

    private WorkflowNode getDecisionNode(Workflow workflow, String decisionNodeId) {
        WorkflowNode node = findNode(workflow, decisionNodeId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Nodo no encontrado: " + decisionNodeId);
        }
        if (node.getTipo() != NodeType.decision) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El nodo no es de tipo decision: " + decisionNodeId);
        }
        return node;
    }

    private ObjectId toObjectId(String value) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ObjectId invalido: " + value);
        }
    }
}
