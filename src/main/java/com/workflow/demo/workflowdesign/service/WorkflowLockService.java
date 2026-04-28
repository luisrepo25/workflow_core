package com.workflow.demo.workflowdesign.service;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.exception.WorkflowNotFoundException;
import com.workflow.demo.repository.WorkflowRepository;
import com.workflow.demo.workflowdesign.dto.WorkflowLockResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowLockService {

    private final WorkflowRepository workflowRepository;

    public WorkflowLockResponse lock(String workflowId, String userId) {
        Workflow workflow = getWorkflow(workflowId);
        ObjectId requesterId = toObjectId(userId);

        if (workflow.getEnEdicionPor() == null || workflow.getEnEdicionPor().equals(requesterId)) {
            workflow.setEnEdicionPor(requesterId);
            workflowRepository.save(workflow);
            return asLocked(workflow);
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Workflow bloqueado por otro usuario: " + workflow.getEnEdicionPor().toHexString());
    }

    public WorkflowLockResponse unlock(String workflowId, String userId) {
        Workflow workflow = getWorkflow(workflowId);
        ObjectId requesterId = toObjectId(userId);

        if (workflow.getEnEdicionPor() == null) {
            return asUnlocked(workflowId);
        }

        if (!workflow.getEnEdicionPor().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo el usuario que tomo el lock puede liberarlo");
        }

        workflow.setEnEdicionPor(null);
        workflowRepository.save(workflow);
        return asUnlocked(workflowId);
    }

    public WorkflowLockResponse status(String workflowId) {
        Workflow workflow = getWorkflow(workflowId);
        if (workflow.getEnEdicionPor() == null) {
            return asUnlocked(workflowId);
        }
        return asLocked(workflow);
    }

    private Workflow getWorkflow(String workflowId) {
        return workflowRepository
            .findById(toObjectId(workflowId))
            .orElseThrow(() -> new WorkflowNotFoundException("Workflow no encontrado: " + workflowId));
    }

    private WorkflowLockResponse asLocked(Workflow workflow) {
        return WorkflowLockResponse.builder()
            .locked(true)
            .workflowId(workflow.getId().toHexString())
            .enEdicionPor(workflow.getEnEdicionPor() != null ? workflow.getEnEdicionPor().toHexString() : null)
            .build();
    }

    private WorkflowLockResponse asUnlocked(String workflowId) {
        return WorkflowLockResponse.builder().locked(false).workflowId(workflowId).enEdicionPor(null).build();
    }

    private ObjectId toObjectId(String value) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ObjectId invalido: " + value);
        }
    }
}