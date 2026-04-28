package com.workflow.demo.workflowdesign.controller;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workflow.demo.domain.entity.WorkflowCollaborator;
import com.workflow.demo.service.WorkflowCollaboratorService;
import com.workflow.demo.workflowdesign.dto.CollaboratorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para invitaciones de colaboración del usuario autenticado.
 */
@Slf4j
@RestController
@RequestMapping("/api/collaborations")
@RequiredArgsConstructor
public class WorkflowInvitationController {

    private final WorkflowCollaboratorService collaboratorService;

    /**
     * GET /api/collaborations/pending
     * Lista las invitaciones pendientes del usuario autenticado.
     */
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CollaboratorResponse>> getPendingInvitations(Authentication authentication) {
        ObjectId userId = new ObjectId(authentication.getName());

        List<WorkflowCollaborator> invitations = collaboratorService.getPendingInvitationsByUser(userId);
        List<CollaboratorResponse> responses = invitations.stream()
            .map(CollaboratorResponse::from)
            .toList();

        log.info("📩 Usuario {} tiene {} invitaciones pendientes", userId.toHexString(), responses.size());
        return ResponseEntity.ok(responses);
    }
}