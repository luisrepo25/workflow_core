package com.workflow.demo.auth.controller;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workflow.demo.domain.entity.Notification;
import com.workflow.demo.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para gestionar notificaciones (Fase 10)
 * 
 * Endpoints:
 * - GET /api/notifications - Obtener mis notificaciones
 * - GET /api/notifications/unread - Obtener notificaciones sin leer
 * - PUT /api/notifications/{id}/read - Marcar como leída
 * - PUT /api/notifications/read-all - Marcar todas como leídas
 * - DELETE /api/notifications/{id} - Eliminar notificación
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROLE_CLIENT', 'ROLE_FUNCIONARIO', 'ROLE_DESIGNER', 'ROLE_ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications
     * Obtiene todas las notificaciones del usuario autenticado
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getNotificaciones(Authentication authentication) {
        log.debug("📋 Obteniendo notificaciones para usuario: {}", authentication.getName());
        
        ObjectId userId = new ObjectId(authentication.getName());
        List<Notification> notificaciones = notificationService.getNotificacionesDelUsuario(userId, 100);
        
        log.info("✅ {} notificaciones obtenidas", notificaciones.size());
        return ResponseEntity.ok(notificaciones);
    }

    /**
     * GET /api/notifications/unread
     * Obtiene solo las notificaciones sin leer
     */
    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getNotificacionesSinLeer(Authentication authentication) {
        log.debug("📋 Obteniendo notificaciones sin leer para usuario: {}", authentication.getName());
        
        ObjectId userId = new ObjectId(authentication.getName());
        List<Notification> notificaciones = notificationService.getNotificacionesPendientes(userId);
        
        log.info("✅ {} notificaciones sin leer", notificaciones.size());
        return ResponseEntity.ok(notificaciones);
    }

    /**
     * GET /api/notifications/unread-count
     * Obtiene el count de notificaciones sin leer
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {
        log.debug("📊 Contando notificaciones sin leer para usuario: {}", authentication.getName());
        
        ObjectId userId = new ObjectId(authentication.getName());
        long count = notificationService.getNotificacionesPendientes(userId).size();
        
        return ResponseEntity.ok().body(
            java.util.Map.of("unreadCount", count)
        );
    }

    /**
     * PUT /api/notifications/{notificationId}/read
     * Marca una notificación específica como leída
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<?> marcarComoLeida(
        @PathVariable String notificationId,
        Authentication authentication
    ) {
        log.info("📍 Marcando notificación {} como leída", notificationId);
        
        try {
            ObjectId notifId = new ObjectId(notificationId);
            notificationService.marcarComoLeida(notifId);
            
            return ResponseEntity.ok().body(
                java.util.Map.of("status", "success", "message", "Notificación marcada como leída")
            );
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ ID de notificación inválido: {}", notificationId);
            return ResponseEntity.badRequest().body(
                java.util.Map.of("error", "ID de notificación inválido")
            );
        }
    }

    /**
     * PUT /api/notifications/read-all
     * Marca TODAS las notificaciones del usuario como leídas
     */
    @PutMapping("/read-all")
    public ResponseEntity<?> marcarTodasComoLeidas(Authentication authentication) {
        log.info("📍 Marcando todas las notificaciones como leídas para usuario: {}", authentication.getName());
        
        ObjectId userId = new ObjectId(authentication.getName());
        notificationService.marcarTodasComoLeidas(userId);
        
        return ResponseEntity.ok().body(
            java.util.Map.of("status", "success", "message", "Todas las notificaciones marcadas como leídas")
        );
    }

    /**
     * DELETE /api/notifications/{notificationId}
     * Elimina una notificación específica
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<?> eliminarNotificacion(
        @PathVariable String notificationId,
        Authentication authentication
    ) {
        log.info("🗑️ Eliminando notificación: {}", notificationId);
        
        try {
            ObjectId notifId = new ObjectId(notificationId);
            notificationService.eliminarNotificacion(notifId);
            
            return ResponseEntity.ok().body(
                java.util.Map.of("status", "success", "message", "Notificación eliminada")
            );
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ ID de notificación inválido: {}", notificationId);
            return ResponseEntity.badRequest().body(
                java.util.Map.of("error", "ID de notificación inválido")
            );
        }
    }

    /**
     * DELETE /api/notifications
     * Elimina TODAS las notificaciones del usuario (destructivo)
     */
    @DeleteMapping
    public ResponseEntity<?> eliminarTodasNotificaciones(Authentication authentication) {
        log.warn("🗑️ Usuario {} está eliminando todas sus notificaciones", authentication.getName());
        
        ObjectId userId = new ObjectId(authentication.getName());
        List<Notification> notificaciones = notificationService.getNotificacionesDelUsuario(userId, 1000);
        
        notificaciones.forEach(n -> notificationService.eliminarNotificacion(n.getId()));
        
        return ResponseEntity.ok().body(
            java.util.Map.of(
                "status", "success",
                "message", "Todas las notificaciones han sido eliminadas",
                "count", notificaciones.size()
            )
        );
    }

}
