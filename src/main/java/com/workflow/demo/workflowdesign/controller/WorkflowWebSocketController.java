package com.workflow.demo.workflowdesign.controller;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.service.AuthorizationService;
import com.workflow.demo.workflowdesign.dto.WorkflowChangeMessage;
import com.workflow.demo.workflowdesign.dto.WorkflowEditingState;
import com.workflow.demo.workflowdesign.dto.WorkflowLockResponse;
import com.workflow.demo.workflowdesign.service.WorkflowCollaborictionService;
import com.workflow.demo.workflowdesign.service.WorkflowLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador WebSocket para edición colaborativa de workflows en tiempo real
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WorkflowWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WorkflowCollaborictionService collaborationService;
    private final WorkflowLockService workflowLockService;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    /**
     * Cliente se conecta a un workflow para editar
     * Ruta: /app/workflow/{workflowId}/connect
     */
    @MessageMapping("/workflow/{workflowId}/connect")
    public void connectToWorkflow(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        try {
            String userId = message.getUserId();
            String userName = message.getUserName();
            
            // Registrar usuario como editor activo
            collaborationService.addActiveEditor(workflowId, userId, userName, "");
            
            // Notificar a otros usuarios que alguien se conectó
            WorkflowChangeMessage notification = WorkflowChangeMessage.builder()
                .action("user_connected")
                .workflowId(workflowId)
                .userId(userId)
                .userName(userName)
                .timestamp(System.currentTimeMillis())
                .status("success")
                .message(userName + " se ha conectado")
                .build();
            
            // Broadcast a todos en este workflow
            messagingTemplate.convertAndSend(
                "/topic/workflow/" + workflowId,
                notification
            );
            
            // Enviar estado actual al usuario que se conectó
            WorkflowEditingState state = collaborationService.getEditingState(workflowId);
            if (state != null) {
                messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/editing-state",
                    state
                );
            }
            
            log.info("Usuario {} conectado a workflow {}", userName, workflowId);
            
        } catch (Exception e) {
            log.error("Error al conectar a workflow: {}", e.getMessage());
            sendError(workflowId, "Error al conectar: " + e.getMessage());
        }
    }

    /**
     * Cliente se desconecta de un workflow
     * Ruta: /app/workflow/{workflowId}/disconnect
     */
    @MessageMapping("/workflow/{workflowId}/disconnect")
    public void disconnectFromWorkflow(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        try {
            String userId = message.getUserId();
            String userName = message.getUserName();
            
            collaborationService.removeActiveEditor(workflowId, userId);
            
            WorkflowChangeMessage notification = WorkflowChangeMessage.builder()
                .action("user_disconnected")
                .workflowId(workflowId)
                .userId(userId)
                .userName(userName)
                .timestamp(System.currentTimeMillis())
                .status("success")
                .message(userName + " se ha desconectado")
                .build();
            
            messagingTemplate.convertAndSend(
                "/topic/workflow/" + workflowId,
                notification
            );
            
            log.info("Usuario {} desconectado de workflow {}", userName, workflowId);
            
        } catch (Exception e) {
            log.error("Error al desconectar de workflow: {}", e.getMessage());
        }
    }

    /**
     * Se agrega un nodo al workflow
     * Ruta: /app/workflow/{workflowId}/node/add
     */
    @MessageMapping("/workflow/{workflowId}/node/add")
    public void addNode(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se actualiza un nodo
     * Ruta: /app/workflow/{workflowId}/node/update
     */
    @MessageMapping("/workflow/{workflowId}/node/update")
    public void updateNode(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se elimina un nodo
     * Ruta: /app/workflow/{workflowId}/node/delete
     */
    @MessageMapping("/workflow/{workflowId}/node/delete")
    public void deleteNode(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se agrega una arista (edge)
     * Ruta: /app/workflow/{workflowId}/edge/add
     */
    @MessageMapping("/workflow/{workflowId}/edge/add")
    public void addEdge(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se elimina una arista
     * Ruta: /app/workflow/{workflowId}/edge/delete
     */
    @MessageMapping("/workflow/{workflowId}/edge/delete")
    public void deleteEdge(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se agrega una lane
     * Ruta: /app/workflow/{workflowId}/lane/add
     */
    @MessageMapping("/workflow/{workflowId}/lane/add")
    public void addLane(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        message.setAction("lane_added");
        message.setData(asLane(message.getData()));
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se actualiza una lane
     * Ruta: /app/workflow/{workflowId}/lane/update
     */
    @MessageMapping("/workflow/{workflowId}/lane/update")
    public void updateLane(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        message.setAction("lane_updated");
        message.setData(asLane(message.getData()));
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Se elimina una lane
     * Ruta: /app/workflow/{workflowId}/lane/delete
     */
    @MessageMapping("/workflow/{workflowId}/lane/delete")
    public void deleteLane(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        message.setAction("lane_deleted");
        handleWorkflowChange(workflowId, message);
    }

    /**
     * Un usuario actualiza su selección/cursor
     * Ruta: /app/workflow/{workflowId}/selection
     */
    @MessageMapping("/workflow/{workflowId}/selection")
    public void updateSelection(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        String userId = message.getUserId();
        String selectedElement = message.getNodeId();
        
        collaborationService.updateEditorSelection(workflowId, userId, selectedElement, null);
        
        // Broadcast a todos para mostrar cursor/selección de otros usuarios
        messagingTemplate.convertAndSend(
            "/topic/workflow/" + workflowId + "/selections",
            message
        );
    }

    /**
     * Obtener estado actual de edición
     * Ruta: /app/workflow/{workflowId}/state
     */
    @MessageMapping("/workflow/{workflowId}/state")
    public void getEditingState(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        WorkflowEditingState state = collaborationService.getEditingState(workflowId);
        
        messagingTemplate.convertAndSendToUser(
            message.getUserId(),
            "/queue/editing-state",
            state
        );
    }

    /**
     * Obtener historial de cambios recientes
     * Ruta: /app/workflow/{workflowId}/history
     */
    @MessageMapping("/workflow/{workflowId}/history")
    public void getChangeHistory(
        @DestinationVariable String workflowId,
        @Payload WorkflowChangeMessage message
    ) {
        var history = collaborationService.getChangeHistory(workflowId);
        
        messagingTemplate.convertAndSendToUser(
            message.getUserId(),
            "/queue/change-history",
            history
        );
    }

    /**
     * Método utilizado para manejar todos los cambios de workflow
     * Valida autorización antes de registrar cambios
     */
    private void handleWorkflowChange(String workflowId, WorkflowChangeMessage message) {
        try {
            message.setWorkflowId(workflowId);
            message.setTimestamp(System.currentTimeMillis());

            log.info("🔄 RECIBIDO cambio en workflow {}: action={}, userId={}, nodeId={}, edgeId={}",
                workflowId, message.getAction(), message.getUserId(), message.getNodeId(), message.getEdgeId());

            String userId = message.getUserId();
            if (userId == null || userId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId es requerido");
            }

            // Validación de autorización para acciones mutantes
            if (isMutatingAction(message.getAction())) {
                // Obtener el workflowId como ObjectId para validación
                org.bson.types.ObjectId workflowObjectId = new org.bson.types.ObjectId(workflowId);
                org.bson.types.ObjectId userObjectId = new org.bson.types.ObjectId(userId);

                // Validar que el usuario puede editar este workflow
                // Solo owner y colaboradores DESIGNER pueden hacer cambios
                boolean canEdit = authorizationService.canEditWorkflow(workflowObjectId, userObjectId);
                if (!canEdit) {
                    log.warn("❌ Usuario {} NO tiene permiso para editar workflow {}", userId, workflowId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No tienes permiso para editar este workflow");
                }

                // Validar lock de edición
                validateEditorLock(workflowId, userId);
                
                log.info("✅ Usuario {} AUTORIZADO para cambio en workflow {}", userId, workflowId);
            }
            
            // Registrar el cambio
            boolean recorded = collaborationService.recordChange(message);
            if (!recorded) {
                log.info("⚠️ Cambio duplicado ignorado: messageId={}", message.getMessageId());
                return;
            }
            
            // Broadcast a todos los usuarios en este workflow
            log.info("📢 BROADCAST a /topic/workflow/{}: action={}", workflowId, message.getAction());
            messagingTemplate.convertAndSend(
                "/topic/workflow/" + workflowId,
                message
            );
            
            log.info("✅ Cambio registrado y broadcast enviado");
            
        } catch (ResponseStatusException rse) {
            if (rse.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("❌ Error de autorización: {}", rse.getReason());
            } else {
                log.error("❌ Error de validación/procesamiento ({}): {}",
                    rse.getStatusCode(), rse.getReason());
            }
            sendError(workflowId, rse.getReason());
        } catch (Exception e) {
            log.error("❌ Error manejando cambio de workflow: {}", e.getMessage(), e);
            sendError(workflowId, "Error procesando cambio: " + e.getMessage());
        }
    }

    private boolean isMutatingAction(String action) {
        return action != null && (action.startsWith("node_")
            || action.startsWith("edge_")
            || action.startsWith("lane_"));
    }

    private void validateEditorLock(String workflowId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId es requerido");
        }

        WorkflowLockResponse lock = workflowLockService.status(workflowId);
        if (lock.isLocked() && lock.getEnEdicionPor() != null && !lock.getEnEdicionPor().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Workflow bloqueado por otro usuario: " + lock.getEnEdicionPor());
        }
    }

    private Lane asLane(Object data) {
        if (data instanceof Lane lane) {
            return lane;
        }
        return objectMapper.convertValue(data, Lane.class);
    }

    /**
     * Enviar error a un workflow
     */
    private void sendError(String workflowId, String errorMessage) {
        WorkflowChangeMessage error = WorkflowChangeMessage.builder()
            .action("error")
            .workflowId(workflowId)
            .status("error")
            .message(errorMessage)
            .timestamp(System.currentTimeMillis())
            .build();
        
        messagingTemplate.convertAndSend("/topic/workflow/" + workflowId, error);
    }
}
