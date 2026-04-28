package com.workflow.demo.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.embedded.FormField;
import com.workflow.demo.domain.embedded.NodeForm;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.entity.Role;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.domain.enums.ActivityStatus;
import com.workflow.demo.domain.enums.ResponsableTipo;
import com.workflow.demo.exception.ActivityAlreadyCompletedException;
import com.workflow.demo.exception.InvalidWorkflowStateException;
import com.workflow.demo.exception.NodeNotActiveException;
import com.workflow.demo.repository.RoleRepository;
import com.workflow.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowValidationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public void validateNodeIsActive(ProcessInstance instance, String nodeId) {
        if (instance.getCurrentNodeIds() == null || !instance.getCurrentNodeIds().contains(nodeId)) {
            throw new NodeNotActiveException("El nodo " + nodeId + " no esta activo en la instancia");
        }
    }

    public ProcessActivity findActividadPendiente(ProcessInstance instance, String nodeId) {
        return instance
            .getActividades()
            .stream()
            .filter(a -> nodeId.equals(a.getNodeId()))
            .filter(a -> a.getEstado() == ActivityStatus.pendiente || a.getEstado() == ActivityStatus.en_ejecucion)
            .findFirst()
            .orElseThrow(() -> new ActivityAlreadyCompletedException(
                "No existe actividad pendiente/en_ejecucion para nodeId " + nodeId));
    }

    public User validatePermisosYObtenerUsuario(ProcessInstance instance, ProcessActivity activity, String usuarioId) {
        ObjectId actorId = parseObjectId(usuarioId);
        User user = userRepository
            .findById(actorId)
            .orElseThrow(() -> new InvalidWorkflowStateException("Usuario no encontrado: " + usuarioId));

        if (!user.isActivo()) {
            throw new InvalidWorkflowStateException("El usuario esta inactivo");
        }

        if (activity.getResponsableTipo() == ResponsableTipo.cliente) {
            if (!Objects.equals(instance.getClienteId(), actorId)) {
                throw new InvalidWorkflowStateException("Solo el cliente propietario puede completar esta actividad");
            }
            return user;
        }

        if (isClienteRole(user.getRoleId())) {
            throw new InvalidWorkflowStateException("Un usuario cliente no puede completar actividades internas");
        }

        if (activity.getResponsableTipo() == ResponsableTipo.usuario && activity.getUsuarioId() != null
            && !activity.getUsuarioId().equals(actorId)) {
            throw new InvalidWorkflowStateException("Actividad asignada a otro usuario");
        }

        if (activity.getResponsableTipo() == ResponsableTipo.usuario && activity.getUsuarioId() == null) {
            throw new InvalidWorkflowStateException("La actividad no tiene usuario asignado");
        }

        if (activity.getResponsableTipo() == ResponsableTipo.departamento && activity.getDepartmentId() != null
            && !Objects.equals(activity.getDepartmentId(), user.getDepartmentId())) {
            throw new InvalidWorkflowStateException("El usuario no pertenece al departamento responsable");
        }

        if (activity.getResponsableTipo() == ResponsableTipo.departamento && activity.getDepartmentId() == null) {
            throw new InvalidWorkflowStateException("La actividad no tiene departamento asignado");
        }

        return user;
    }

    public void validateFormulario(WorkflowNode node, Map<String, Object> respuestaFormulario) {
        NodeForm form = node.getForm();
        if (form == null || form.getCampos() == null || form.getCampos().isEmpty()) {
            return;
        }

        Map<String, Object> payload = respuestaFormulario == null ? Map.of() : respuestaFormulario;
        List<FormField> requiredFields = form.getCampos().stream().filter(FormField::isRequired).toList();

        for (FormField field : requiredFields) {
            Object value = payload.get(field.getId());
            if (value == null || (value instanceof String s && s.isBlank())) {
                throw new InvalidWorkflowStateException(
                    "Campo requerido faltante: " + field.getId() + " en nodo " + node.getId());
            }
        }
    }

    private ObjectId parseObjectId(String value) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidWorkflowStateException("Id no valido: " + value);
        }
    }

    private boolean isClienteRole(ObjectId roleId) {
        if (roleId == null) {
            return false;
        }
        return roleRepository.findById(roleId).map(Role::getNombre).map("Cliente"::equalsIgnoreCase).orElse(false);
    }
}