package com.workflow.demo.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.demo.domain.embedded.NotificationReference;
import com.workflow.demo.domain.embedded.PushMeta;
import com.workflow.demo.domain.embedded.PushToken;
import com.workflow.demo.domain.entity.Notification;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.enums.NotificationStatus;
import com.workflow.demo.domain.enums.NotificationType;
import com.workflow.demo.repository.NotificationRepository;
import com.workflow.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de Notificaciones (Fase 10)
 * 
 * Gestiona notificaciones para:
 * - Asignación de actividades
 * - Cambios de estado en procesos
 * - Aprobaciones requeridas
 * - Completación de procesos
 * - Cambios en workflows
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${api.notification:}")
    private String notificationApiBaseUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Notifica que una actividad fue asignada al usuario
     * Se llama cuando WorkflowEngineService crea una nueva ProcessActivity
     */
    public void notificarActividadAsignada(ObjectId userId, ProcessInstance instance, WorkflowNode node) {
        if (userId == null || instance == null || node == null) {
            log.warn("⚠️ Datos incompletos para notificación de actividad asignada");
            return;
        }

        Notification notification = baseNotification(userId, instance, node);
        notification.setTipo(NotificationType.push_in_app);
        notification.setEstado(NotificationStatus.pendiente_envio);
        notification.setTitulo("📋 Nueva actividad asignada");
        notification.setMensaje("Se asignó la actividad '" + node.getNombre() + "' al proceso " + instance.getCodigo());
        notification.setCreatedAt(Instant.now());
        
        notificationRepository.save(notification);
        log.info("📬 Notificación enviada: Usuario {} - Actividad {}", userId.toHexString(), node.getId());
    }

    /**
     * Notifica al usuario que una actividad le fue reasignada
     * Se llama cuando ActivityAssignmentService.reassignActivity()
     */
    public void notificarActividadReasignada(ObjectId userId, ProcessInstance instance, WorkflowNode node, String previousUser) {
        if (userId == null) return;

        Notification notification = baseNotification(userId, instance, node);
        notification.setTipo(NotificationType.push_in_app);
        notification.setEstado(NotificationStatus.pendiente_envio);
        notification.setTitulo("🔄 Actividad reasignada");
        notification.setMensaje("La actividad '" + node.getNombre() + "' fue reasignada a ti desde " + previousUser);
        notification.setCreatedAt(Instant.now());
        
        notificationRepository.save(notification);
        log.info("📬 Notificación reasignación enviada a {}", userId.toHexString());
    }

    /**
     * Notifica que el proceso fue completado/finalizado
     */
    public void notificarProcesoFinalizado(ObjectId clienteId, ProcessInstance instance) {
        if (clienteId == null || instance == null) return;

        Notification notification = baseNotification(clienteId, instance, null);
        notification.setTipo(NotificationType.push_in_app);
        notification.setEstado(NotificationStatus.pendiente_envio);
        notification.setTitulo("✅ Trámite completado");
        notification.setMensaje("El trámite " + instance.getCodigo() + " fue finalizado exitosamente.");
        notification.setCreatedAt(Instant.now());
        
        notificationRepository.save(notification);
        log.info("📬 Notificación finalización enviada al cliente {}", clienteId.toHexString());

        enviarPushAlCliente(
            clienteId,
            "Trámite completado",
            "El trámite " + instance.getCodigo() + " fue finalizado exitosamente."
        );
    }

    public void notificarTramiteCreado(ProcessInstance instance) {
        if (instance == null || instance.getClienteId() == null) {
            return;
        }

        enviarPushAlCliente(
            instance.getClienteId(),
            "Trámite creado",
            "Tu trámite " + instance.getCodigo() + " fue creado correctamente."
        );
    }

    public void notificarActividadCompletadaPorFuncionario(ProcessInstance instance, String nombreActividad) {
        if (instance == null || instance.getClienteId() == null) {
            return;
        }

        String detalleActividad = (nombreActividad == null || nombreActividad.isBlank())
            ? "una actividad"
            : "la actividad '" + nombreActividad + "'";

        enviarPushAlCliente(
            instance.getClienteId(),
            "Actividad actualizada",
            "Se completó " + detalleActividad + " en tu trámite " + instance.getCodigo() + "."
        );
    }

    /**
     * Notifica que un proceso entró en estado EN_PROCESO
     */
    public void notificarProcesoEnProceso(ObjectId clienteId, ProcessInstance instance) {
        if (clienteId == null) return;

        Notification notification = baseNotification(clienteId, instance, null);
        notification.setTipo(NotificationType.push_in_app);
        notification.setEstado(NotificationStatus.pendiente_envio);
        notification.setTitulo("⏱️ Trámite en proceso");
        notification.setMensaje("El trámite " + instance.getCodigo() + " está siendo procesado.");
        notification.setCreatedAt(Instant.now());
        
        notificationRepository.save(notification);
        log.info("📬 Notificación en proceso enviada al cliente {}", clienteId.toHexString());
    }

    /**
     * Notifica a un departamento sobre actividades pendientes
     */
    public void notificarActividadesPendientesDepartamento(ObjectId departmentId, long count) {
        if (departmentId == null || count == 0) return;

        log.info("📬 Notificando departamento {} sobre {} actividades pendientes", departmentId.toHexString(), count);
        // TODO: Implementar lógica para notificar a todos los usuarios del departamento
        // Buscar usuarios por departmentId y crear notificaciones en masa
    }

    /**
     * Obtiene todas las notificaciones pendientes de un usuario
     */
    public List<Notification> getNotificacionesPendientes(ObjectId userId) {
        log.debug("📋 Buscando notificaciones pendientes para usuario {}", userId.toHexString());
        List<Notification> notificaciones = notificationRepository.findByUserIdAndEstado(
            userId, 
            NotificationStatus.pendiente_envio
        );
        log.info("✅ Encontradas {} notificaciones pendientes", notificaciones.size());
        return notificaciones;
    }

    /**
     * Obtiene todas las notificaciones de un usuario (leídas y sin leer)
     */
    public List<Notification> getNotificacionesDelUsuario(ObjectId userId, int limit) {
        log.debug("📋 Obteniendo {} notificaciones del usuario {}", limit, userId.toHexString());
        return notificationRepository.findByUserId(userId);
    }

    /**
     * Marca una notificación como leída
     */
    public void marcarComoLeida(ObjectId notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setLeida(true);
            notification.setLeIdoAt(Instant.now());
            notificationRepository.save(notification);
            log.info("✅ Notificación marcada como leída: {}", notificationId.toHexString());
        });
    }

    /**
     * Marca todas las notificaciones de un usuario como leídas
     */
    public void marcarTodasComoLeidas(ObjectId userId) {
        List<Notification> notificaciones = notificationRepository.findByUserIdAndLeida(userId, false);
        notificaciones.forEach(n -> {
            n.setLeida(true);
            n.setLeIdoAt(Instant.now());
        });
        notificationRepository.saveAll(notificaciones);
        log.info("✅ {} notificaciones marcadas como leídas", notificaciones.size());
    }

    /**
     * Marca una notificación como enviada
     * Se llama después de enviar a través de email, SMS, push, etc.
     */
    public void marcarComoEnviada(ObjectId notificationId, String channel) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setEstado(NotificationStatus.enviada);
            notification.setEnviadoAt(Instant.now());
            notification.setCanal(channel);
            notificationRepository.save(notification);
            log.info("✅ Notificación enviada por {}: {}", channel, notificationId.toHexString());
        });
    }

    /**
     * Marca una notificación como fallida
     */
    public void marcarComoFallida(ObjectId notificationId, String motivo) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setEstado(NotificationStatus.fallida);
            notification.setMotivoFalla(motivo);
            notificationRepository.save(notification);
            log.warn("❌ Notificación fallida {}: {}", notificationId.toHexString(), motivo);
        });
    }

    /**
     * Elimina una notificación
     */
    public void eliminarNotificacion(ObjectId notificationId) {
        notificationRepository.deleteById(notificationId);
        log.info("🗑️ Notificación eliminada: {}", notificationId.toHexString());
    }

    /**
     * Constructor base para todas las notificaciones
     */
    private Notification baseNotification(ObjectId userId, ProcessInstance instance, WorkflowNode node) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setLeida(false);
        notification.setEstado(NotificationStatus.pendiente_envio);
        notification.setPushMeta(new PushMeta());
        notification.setCreatedAt(Instant.now());

        NotificationReference reference = new NotificationReference();
        reference.setProcessInstanceId(instance.getId());
        reference.setWorkflowId(instance.getWorkflowId());
        if (node != null) {
            reference.setNodeId(node.getId());
        }
        notification.setReferencia(reference);
        return notification;
    }

    private void enviarPushAlCliente(ObjectId clienteId, String titulo, String mensaje) {
        if (clienteId == null || titulo == null || mensaje == null) {
            return;
        }

        User cliente = userRepository.findById(clienteId).orElse(null);
        if (cliente == null) {
            log.warn("No se pudo enviar push: cliente {} no encontrado", clienteId.toHexString());
            return;
        }

        String fcmToken = resolveFcmToken(cliente);
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("No se pudo enviar push: cliente {} no tiene fcm_token", clienteId.toHexString());
            return;
        }

        String baseUrl = notificationApiBaseUrl == null ? "" : notificationApiBaseUrl.trim();
        if (baseUrl.isBlank()) {
            log.warn("No se pudo enviar push: api.notification no configurado");
            return;
        }

        String endpoint = baseUrl.endsWith("/")
            ? baseUrl + "notification/send"
            : baseUrl + "/notification/send";

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "titulo", titulo,
                "mensaje", mensaje,
                "fcm_token", fcmToken
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Push enviado correctamente a cliente {}", clienteId.toHexString());
            } else {
                log.warn("Push falló para cliente {}. HTTP {}. Respuesta: {}",
                    clienteId.toHexString(), response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("Error enviando push a cliente {}: {}", clienteId.toHexString(), ex.getMessage());
        }
    }

    private String resolveFcmToken(User user) {
        if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            return user.getFcmToken();
        }

        if (user.getPushTokens() == null || user.getPushTokens().isEmpty()) {
            return null;
        }

        return user.getPushTokens().stream()
            .filter(PushToken::isActivo)
            .max(Comparator.comparing(t -> t.getLastUsedAt() == null ? Instant.MIN : t.getLastUsedAt()))
            .map(PushToken::getToken)
            .orElse(null);
    }
}