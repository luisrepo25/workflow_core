package com.workflow.demo.workflowdesign.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.workflow.demo.workflowdesign.dto.CreateWorkflowRequest;
import com.workflow.demo.workflowdesign.dto.DecisionContextResponse;
import com.workflow.demo.workflowdesign.dto.DecisionRulesPatchRequest;
import com.workflow.demo.workflowdesign.dto.DecisionRulesPatchResponse;
import com.workflow.demo.workflowdesign.dto.DecisionRulesSimulateRequest;
import com.workflow.demo.workflowdesign.dto.DecisionRulesSimulateResponse;
import com.workflow.demo.workflowdesign.dto.DecisionRulesValidateRequest;
import com.workflow.demo.workflowdesign.dto.DecisionRulesValidationResult;
import com.workflow.demo.workflowdesign.dto.LockWorkflowRequest;
import com.workflow.demo.workflowdesign.dto.SaveWorkflowDesignRequest;
import com.workflow.demo.workflowdesign.dto.UpdateWorkflowRequest;
import com.workflow.demo.workflowdesign.dto.WorkflowDetailResponse;
import com.workflow.demo.workflowdesign.dto.WorkflowListItemResponse;
import com.workflow.demo.workflowdesign.dto.WorkflowLockResponse;
import com.workflow.demo.workflowdesign.dto.WorkflowValidationResponse;
import com.workflow.demo.workflowdesign.service.DecisionRuleService;
import com.workflow.demo.workflowdesign.service.WorkflowLockService;
import com.workflow.demo.workflowdesign.service.WorkflowService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowLockService workflowLockService;
    private final DecisionRuleService decisionRuleService;

    @GetMapping
    public List<WorkflowListItemResponse> listar(
        @RequestParam(required = false) String estado,
        @RequestParam(required = false) String nombre
    ) {
        return workflowService.listar(estado, nombre);
    }

    @GetMapping("/active-for-processes")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN')")
    public List<WorkflowListItemResponse> listarActivosParaTramites() {
        return workflowService.listarActivosParaTramites();
    }

    @GetMapping("/{workflowId}")
    public WorkflowDetailResponse obtenerPorId(@PathVariable String workflowId) {
        return workflowService.obtenerPorId(workflowId);
    }

    @PostMapping
    public WorkflowDetailResponse crear(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflowService.crear(request);
    }

    @PatchMapping("/{workflowId}")
    public WorkflowDetailResponse actualizarMetadata(
        @PathVariable String workflowId,
        @RequestBody UpdateWorkflowRequest request
    ) {
        return workflowService.actualizarMetadata(workflowId, request);
    }

    @PutMapping("/{workflowId}/design")
    public WorkflowDetailResponse guardarDiseno(
        @PathVariable String workflowId,
        @RequestBody SaveWorkflowDesignRequest request
    ) {
        return workflowService.guardarDiseno(workflowId, request);
    }

    @PostMapping("/{workflowId}/validate")
    public WorkflowValidationResponse validar(@PathVariable String workflowId) {
        return workflowService.validar(workflowId);
    }

    @PostMapping("/{workflowId}/activate")
    public WorkflowDetailResponse activar(@PathVariable String workflowId) {
        return workflowService.activar(workflowId);
    }

    @PostMapping("/{workflowId}/deactivate")
    public WorkflowDetailResponse desactivar(@PathVariable String workflowId) {
        return workflowService.desactivar(workflowId);
    }

    @DeleteMapping("/{workflowId}")
    public WorkflowDetailResponse eliminarLogico(@PathVariable String workflowId) {
        return workflowService.inactivarPorDelete(workflowId);
    }

    @GetMapping("/{workflowId}/decision-context/{decisionNodeId}")
    public DecisionContextResponse getDecisionContext(
        @PathVariable String workflowId,
        @PathVariable String decisionNodeId
    ) {
        return decisionRuleService.getDecisionContext(workflowId, decisionNodeId);
    }

    @PatchMapping("/{workflowId}/nodes/{decisionNodeId}/decision-rules")
    public DecisionRulesPatchResponse patchDecisionRules(
        @PathVariable String workflowId,
        @PathVariable String decisionNodeId,
        @RequestBody DecisionRulesPatchRequest request
    ) {
        return decisionRuleService.patchDecisionRules(
            workflowId,
            decisionNodeId,
            request.getDecisionRule(),
            request.getMode()
        );
    }

    @PostMapping("/{workflowId}/nodes/{decisionNodeId}/decision-rules/validate")
    public DecisionRulesValidationResult validateDecisionRules(
        @PathVariable String workflowId,
        @PathVariable String decisionNodeId,
        @RequestBody DecisionRulesValidateRequest request
    ) {
        return decisionRuleService.validateDecisionRules(
            workflowId,
            decisionNodeId,
            request.getDecisionRule(),
            request.getMode()
        );
    }

    @PostMapping("/{workflowId}/nodes/{decisionNodeId}/decision-rules/simulate")
    public DecisionRulesSimulateResponse simulateDecisionRules(
        @PathVariable String workflowId,
        @PathVariable String decisionNodeId,
        @RequestBody DecisionRulesSimulateRequest request
    ) {
        return decisionRuleService.simulateDecisionRules(
            workflowId,
            decisionNodeId,
            request.getInput(),
            request.getDecisionRule(),
            request.getMode()
        );
    }

    @PostMapping("/{workflowId}/lock")
    public WorkflowLockResponse tomarLock(
        @PathVariable String workflowId,
        @Valid @RequestBody LockWorkflowRequest request
    ) {
        return workflowLockService.lock(workflowId, request.getUserId());
    }

    @DeleteMapping("/{workflowId}/lock")
    public WorkflowLockResponse liberarLock(
        @PathVariable String workflowId,
        @RequestParam String userId
    ) {
        return workflowLockService.unlock(workflowId, userId);
    }

    @GetMapping("/{workflowId}/lock")
    public WorkflowLockResponse estadoLock(@PathVariable String workflowId) {
        return workflowLockService.status(workflowId);
    }
}