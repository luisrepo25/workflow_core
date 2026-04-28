package com.workflow.demo.workflowdesign.scheduler;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.workflow.demo.workflowdesign.service.WorkflowCollaborictionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler para limpiar sesiones expiradas de edición colaborativa
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class CollaborationCleanupScheduler {

    private final WorkflowCollaborictionService collaborationService;

    /**
     * Ejecutar limpieza cada 5 minutos
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void cleanupExpiredSessions() {
        try {
            collaborationService.cleanupExpiredSessions();
            log.debug("Limpieza de sesiones expiradas completada");
        } catch (Exception e) {
            log.error("Error durante limpieza de sesiones: {}", e.getMessage());
        }
    }
}
