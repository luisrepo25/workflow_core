package com.workflow.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.embedded.HistoryEvent;
import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.Department;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.domain.enums.ActivityStatus;
import com.workflow.demo.domain.enums.ResponsableTipo;
import com.workflow.demo.exception.ProcessInstanceNotFoundException;
import com.workflow.demo.repository.ProcessInstanceRepository;
import com.workflow.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestionar asignación y enrutamiento de actividades en procesos
 * 
 * Responsabilidades:
 * - Buscar actividades pendientes para un usuario/departamento
 * - Validar que un usuario puede ejecutar una actividad
 * - Reasignar actividades si es necesario
 * - Manejar actividades por cliente, usuario específico, o departamento
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityAssignmentService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final UserRepository userRepository;

    /**
     * Obtiene todas las actividades pendientes para un funcionario específico, junto con su instancia de proceso.
     * 
     * @param usuarioId ID del usuario (funcionario)
     * @return Mapa de Actividad -> Instancia de Proceso
     */
    public Map<ProcessActivity, ProcessInstance> getPendingActivitiesWithInstancesForUser(ObjectId usuarioId) {
        log.debug("📋 Buscando actividades pendientes para usuario: {}", usuarioId);

        Optional<User> userOpt = userRepository.findById(usuarioId);
        if (userOpt.isEmpty()) {
            log.warn("⚠️ Usuario no encontrado: {}", usuarioId);
            return new HashMap<>();
        }

        User user = userOpt.get();
        ObjectId departmentId = user.getDepartmentId();
        log.info("🔍 Buscando pendientes para funcionario: {} (Depto: {})", user.getNombre(), departmentId);

        // Buscar todos los procesos activos en una sola consulta eficiente
        List<ProcessInstance> allActive = processInstanceRepository.findActiveProcesses();

        Map<ProcessActivity, ProcessInstance> results = new LinkedHashMap<>();

        for (ProcessInstance instance : allActive) {
            if (instance.getActividades() == null) continue;

            for (ProcessActivity activity : instance.getActividades()) {
                // Solo actividades pendientes
                if (activity.getEstado() != ActivityStatus.pendiente) {
                    continue;
                }

                // Verificar si la actividad es para este usuario o su departamento
                boolean assignedToUser = ResponsableTipo.usuario == activity.getResponsableTipo()
                    && usuarioId.equals(activity.getUsuarioId());

                boolean assignedToDepartment = false;
                if (ResponsableTipo.departamento == activity.getResponsableTipo()) {
                    ObjectId actDeptId = activity.getDepartmentId();
                    
                    // Fallback: si no tiene departmentId, intentar recuperarlo del snapshot del proceso
                    if (actDeptId == null && instance.getWorkflowSnapshot() != null) {
                        WorkflowNode node = instance.getWorkflowSnapshot().getNodeById(activity.getNodeId());
                        if (node != null && node.getLaneId() != null) {
                            Lane lane = instance.getWorkflowSnapshot().getLaneById(node.getLaneId());
                            if (lane != null && lane.getDepartmentId() != null) {
                                actDeptId = lane.getDepartmentId();
                                log.info("   ↳ 🔄 Recuperando departmentId {} desde snapshot para actividad {} en {}", 
                                    actDeptId, activity.getActividadId(), instance.getCodigo());
                                // Corregimos en memoria el objeto de la instancia
                                activity.setDepartmentId(actDeptId);
                            }
                        }
                    }
                    
                    assignedToDepartment = departmentId != null && departmentId.equals(actDeptId);
                }

                if (assignedToUser || assignedToDepartment) {
                    results.put(activity, instance);
                    log.info("✅ Actividad coincidente: {} en instancia {}", activity.getActividadId(), instance.getCodigo());
                } else {
                    logActivityMismatch(usuarioId, departmentId, activity, instance.getCodigo());
                }
            }
        }

        log.info("📊 Resultado: {} actividades encontradas para {}", results.size(), user.getNombre());
        if (results.isEmpty()) {
            log.warn("❓ No se encontraron actividades para el usuario {}. Depto Usuario: {}", usuarioId, departmentId);
        }
        return results;
    }

    private void logActivityMismatch(ObjectId userId, ObjectId userDeptId, ProcessActivity activity, String processCode) {
        log.debug("🕵️ Desajuste en actividad {} ({}): Tipo={}, UsuarioAsig={}, DeptoAsig={}. UsuarioReq={}, DeptoReq={}",
            activity.getActividadId(), processCode, activity.getResponsableTipo(), 
            activity.getUsuarioId(), activity.getDepartmentId(),
            userId, userDeptId);
    }

    /**
     * Obtiene actividades pendientes de un cliente específico.
     * Estas son actividades que requieren participación del cliente.
     * 
     * @param clienteId ID del cliente
     * @return Lista de actividades pendientes del cliente
     */
    public List<ProcessActivity> getPendingActivitiesForClient(ObjectId clienteId) {
        log.debug("📋 Buscando actividades pendientes para cliente: {}", clienteId);

        List<ProcessInstance> instances = processInstanceRepository.findByClienteId(clienteId);
        List<ProcessActivity> pendingActivities = new ArrayList<>();

        for (ProcessInstance instance : instances) {
            if (instance.getActividades() == null) continue;

            for (ProcessActivity activity : instance.getActividades()) {
                if (activity.getEstado() == ActivityStatus.pendiente
                    && ResponsableTipo.cliente == activity.getResponsableTipo()) {
                    pendingActivities.add(activity);
                    log.debug("✅ Actividad cliente encontrada: {}", activity.getActividadId());
                }
            }
        }

        log.info("📊 Cliente {} tiene {} actividades pendientes", clienteId, pendingActivities.size());
        return pendingActivities;
    }

    /**
     * Obtiene actividades pendientes de un departamento específico.
     * 
     * @param departmentId ID del departamento
     * @return Lista de actividades pendientes del departamento
     */
    public List<ProcessActivity> getPendingActivitiesByDepartment(ObjectId departmentId) {
        log.debug("📋 Buscando actividades pendientes para departamento: {}", departmentId);

        List<ProcessInstance> instances = processInstanceRepository.findAll();
        List<ProcessActivity> pendingActivities = new ArrayList<>();

        for (ProcessInstance instance : instances) {
            if (instance.getActividades() == null) continue;

            for (ProcessActivity activity : instance.getActividades()) {
                if (activity.getEstado() == ActivityStatus.pendiente
                    && ResponsableTipo.departamento == activity.getResponsableTipo()
                    && departmentId.equals(activity.getDepartmentId())) {
                    pendingActivities.add(activity);
                    log.debug("✅ Actividad departamento encontrada: {}", activity.getActividadId());
                }
            }
        }

        log.info("📊 Departamento {} tiene {} actividades pendientes", departmentId, pendingActivities.size());
        return pendingActivities;
    }

    /**
     * Valida que un usuario puede ejecutar una actividad específica.
     * 
     * Verifica:
     * - La actividad existe y está pendiente
     * - El usuario tiene permiso (asignado directamente o pertenece al departamento)
     * 
     * @param processInstanceId ID del proceso
     * @param actividadId ID de la actividad
     * @param usuarioId ID del usuario
     * @return true si el usuario puede ejecutar la actividad
     */
    public boolean canUserExecuteActivity(ObjectId processInstanceId, String actividadId, ObjectId usuarioId) {
        log.debug("🔐 Validando permisos: usuario {} para actividad {}", usuarioId, actividadId);

        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new ProcessInstanceNotFoundException(
                "No existe process_instance con id " + processInstanceId));

        ProcessActivity activity = instance.getActividades()
            .stream()
            .filter(a -> a.getActividadId().equals(actividadId))
            .findFirst()
            .orElse(null);

        if (activity == null) {
            log.warn("⚠️ Actividad no encontrada: {}", actividadId);
            return false;
        }

        // La actividad debe estar pendiente
        if (activity.getEstado() != ActivityStatus.pendiente) {
            log.warn("⚠️ Actividad no está pendiente: {} - Estado: {}", actividadId, activity.getEstado());
            return false;
        }

        Optional<User> userOpt = userRepository.findById(usuarioId);
        if (userOpt.isEmpty()) {
            log.warn("⚠️ Usuario no encontrado: {}", usuarioId);
            return false;
        }

        User user = userOpt.get();

        // Verificar asignación
        boolean canExecute = false;

        if (ResponsableTipo.usuario == activity.getResponsableTipo()) {
            // Actividad asignada a usuario específico
            if (activity.getUsuarioId() == null) {
                log.warn("⚠️ Actividad {} de tipo usuario no tiene usuarioId", actividadId);
                return false;
            }
            canExecute = usuarioId.equals(activity.getUsuarioId());
            log.debug("🔍 Asignación a usuario específico: {}", canExecute);
        } else if (ResponsableTipo.departamento == activity.getResponsableTipo()) {
            // Actividad asignada a departamento
            if (activity.getDepartmentId() == null) {
                log.warn("⚠️ Actividad {} de tipo departamento no tiene departmentId", actividadId);
                return false;
            }
            canExecute = user.getDepartmentId() != null && user.getDepartmentId().equals(activity.getDepartmentId());
            log.debug("🔍 Asignación a departamento: {}", canExecute);
        } else if (ResponsableTipo.cliente == activity.getResponsableTipo()) {
            // El usuario es el cliente
            canExecute = usuarioId.equals(instance.getClienteId());
            log.debug("🔍 Actividad cliente: {}", canExecute);
        }

        if (canExecute) {
            log.info("✅ Usuario {} PUEDE ejecutar actividad {}", usuarioId, actividadId);
        } else {
            log.warn("❌ Usuario {} NO puede ejecutar actividad {}", usuarioId, actividadId);
        }

        return canExecute;
    }

    /**
     * Reasigna una actividad a un usuario diferente.
     * Útil cuando un funcionario está enfermo, de vacaciones, etc.
     * 
     * @param processInstanceId ID del proceso
     * @param actividadId ID de la actividad
     * @param newUsuarioId ID del nuevo usuario asignado
     * @return true si la reasignación fue exitosa
     */
    public boolean reassignActivity(ObjectId processInstanceId, String actividadId, ObjectId newUsuarioId) {
        log.info("🔄 Reasignando actividad {} a usuario {}", actividadId, newUsuarioId);

        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new ProcessInstanceNotFoundException(
                "No existe process_instance con id " + processInstanceId));

        ProcessActivity activity = instance.getActividades()
            .stream()
            .filter(a -> a.getActividadId().equals(actividadId))
            .findFirst()
            .orElse(null);

        if (activity == null) {
            log.warn("⚠️ Actividad no encontrada para reasignación: {}", actividadId);
            return false;
        }

        ObjectId oldUsuarioId = activity.getUsuarioId();
        activity.setUsuarioId(newUsuarioId);
        processInstanceRepository.save(instance);

        log.info("✅ Actividad reasignada: {} - De: {} - Para: {}", actividadId, oldUsuarioId, newUsuarioId);
        return true;
    }

    /**
     * Obtiene todas las actividades de un proceso en orden.
     * 
     * @param processInstanceId ID del proceso
     * @return Lista de todas las actividades del proceso
     */
    public List<ProcessActivity> getProcessActivities(ObjectId processInstanceId) {
        log.debug("📋 Obteniendo actividades del proceso: {}", processInstanceId);

        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new ProcessInstanceNotFoundException(
                "No existe process_instance con id " + processInstanceId));

        return instance.getActividades() != null ? instance.getActividades() : new ArrayList<>();
    }

    /**
     * Obtiene una actividad específica dentro de un proceso.
     * 
     * @param processInstanceId ID del proceso
     * @param actividadId ID de la actividad
     * @return Optional con la actividad si existe
     */
    public Optional<ProcessActivity> getActivity(ObjectId processInstanceId, String actividadId) {
        log.debug("🔍 Buscando actividad {} en proceso {}", actividadId, processInstanceId);

        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow(() -> new ProcessInstanceNotFoundException(
                "No existe process_instance con id " + processInstanceId));

        return instance.getActividades()
            .stream()
            .filter(a -> a.getActividadId().equals(actividadId))
            .findFirst();
    }

}
