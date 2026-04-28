package com.workflow.demo.domain.enums;

import lombok.Getter;

/**
 * Enumeración oficial de roles del sistema.
 * Estos roles definen los permisos y responsabilidades de cada usuario.
 */
@Getter
public enum RoleEnum {
    /**
     * Diseñador: Puede crear y editar workflows, diseñar procesos
     */
    DESIGNER("ROLE_DESIGNER", "Diseñador", "Diseña y edita workflows"),
    
    /**
     * Funcionario: Ejecuta actividades asignadas dentro de procesos activos
     */
    FUNCIONARIO("ROLE_FUNCIONARIO", "Funcionario", "Ejecuta actividades de procesos"),
    
    /**
     * Cliente: Consulta el estado de sus trámites (solo lectura)
     */
    CLIENT("ROLE_CLIENT", "Cliente", "Consulta estado de trámites"),
    
    /**
     * Administrador: Acceso total al sistema
     */
    ADMIN("ROLE_ADMIN", "Administrador", "Administrador del sistema");

    private final String authority;      // Nombre de autoridad Spring Security (ROLE_*)
    private final String displayName;    // Nombre legible en español
    private final String description;    // Descripción de responsabilidades

    RoleEnum(String authority, String displayName, String description) {
        this.authority = authority;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Obtiene el RoleEnum a partir del nombre legible
     */
    public static RoleEnum fromDisplayName(String displayName) {
        for (RoleEnum role : values()) {
            if (role.displayName.equalsIgnoreCase(displayName)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Rol no válido: " + displayName);
    }

    /**
     * Obtiene el RoleEnum a partir de la autoridad
     */
    public static RoleEnum fromAuthority(String authority) {
        for (RoleEnum role : values()) {
            if (role.authority.equals(authority)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Autoridad no válida: " + authority);
    }
}
