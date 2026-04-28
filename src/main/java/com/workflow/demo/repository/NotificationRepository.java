package com.workflow.demo.repository;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.workflow.demo.domain.entity.Notification;
import com.workflow.demo.domain.enums.NotificationStatus;

public interface NotificationRepository extends MongoRepository<Notification, ObjectId> {
    
    /**
     * Obtiene todas las notificaciones de un usuario ordenadas por fecha
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(ObjectId userId);

    /**
     * Obtiene todas las notificaciones de un usuario (sin orden específico)
     */
    List<Notification> findByUserId(ObjectId userId);

    /**
     * Obtiene notificaciones sin leer de un usuario
     */
    List<Notification> findByUserIdAndLeidaFalse(ObjectId userId);
    
    /**
     * Obtiene notificaciones por usuario y estado (Fase 10)
     */
    List<Notification> findByUserIdAndEstado(ObjectId userId, NotificationStatus estado);
    
    /**
     * Obtiene notificaciones por usuario y si fueron leídas
     */
    List<Notification> findByUserIdAndLeida(ObjectId userId, boolean leida);
    
    /**
     * Obtiene notificaciones pendientes de envío
     */
    @Query("{ 'estado': 'pendiente_envio' }")
    List<Notification> findPendingNotifications();
    
    /**
     * Obtiene notificaciones fallidas
     */
    @Query("{ 'estado': 'fallida' }")
    List<Notification> findFailedNotifications();
    
    /**
     * Cuenta notificaciones sin leer para un usuario
     */
    long countByUserIdAndLeidaFalse(ObjectId userId);
    
    /**
     * Cuenta notificaciones por estado
     */
    long countByEstado(NotificationStatus estado);
}