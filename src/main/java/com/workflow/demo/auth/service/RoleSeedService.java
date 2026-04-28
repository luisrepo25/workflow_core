package com.workflow.demo.auth.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.entity.Role;
import com.workflow.demo.domain.enums.RoleEnum;
import com.workflow.demo.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de inicialización de roles del sistema.
 * Se ejecuta al iniciar la aplicación para garantizar que los roles estén presentes en la BD.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleSeedService {

    private final RoleRepository roleRepository;

    /**
     * Se ejecuta cuando la aplicación Spring está lista.
     * Crea los roles iniciales del sistema si no existen.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedRoles() {
        log.info("🌱 Inicializando roles del sistema...");
        
        for (RoleEnum roleEnum : RoleEnum.values()) {
            try {
                // Verificar si el rol ya existe
                if (!roleRepository.findByNombre(roleEnum.getDisplayName()).isPresent()) {
                    Role role = new Role();
                    role.setNombre(roleEnum.getDisplayName());
                    role.setActivo(true);
                    
                    // Asignar permisos básicos según el rol
                    role.setPermisos(getPermisosForRole(roleEnum));
                    
                    roleRepository.save(role);
                    log.info("✅ Rol creado: {}", roleEnum.getDisplayName());
                } else {
                    log.info("ℹ️  Rol ya existe: {}", roleEnum.getDisplayName());
                }
            } catch (Exception e) {
                log.error("❌ Error al crear rol {}: {}", roleEnum.getDisplayName(), e.getMessage());
            }
        }
        
        log.info("✅ Inicialización de roles completada");
    }

    /**
     * Define los permisos básicos para cada rol.
     */
    private List<String> getPermisosForRole(RoleEnum roleEnum) {
        return switch (roleEnum) {
            case DESIGNER -> Arrays.asList(
                "workflow.create",
                "workflow.edit",
                "workflow.design",
                "workflow.publish",
                "workflow.view",
                "user.invite",
                "user.collaborators.manage"
            );
            case FUNCIONARIO -> Arrays.asList(
                "workflow.view",
                "activity.view",
                "activity.complete",
                "process.view",
                "notification.view"
            );
            case CLIENT -> Arrays.asList(
                "process.view.own",
                "activity.view.own",
                "notification.view"
            );
            case ADMIN -> Arrays.asList(
                "*"  // Acceso total
            );
        };
    }
}
