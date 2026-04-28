package com.workflow.demo.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.domain.embedded.DecisionRule;
import com.workflow.demo.domain.embedded.HistoryEvent;
import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.embedded.WorkflowSnapshot;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.ActivityStatus;
import com.workflow.demo.domain.enums.EdgeType;
import com.workflow.demo.domain.enums.HistoryEventType;
import com.workflow.demo.domain.enums.NodeType;
import com.workflow.demo.domain.enums.ProcessStatus;
import com.workflow.demo.domain.enums.ResponsableTipo;
import com.workflow.demo.exception.DecisionResolutionException;
import com.workflow.demo.exception.InvalidWorkflowStateException;
import com.workflow.demo.exception.ProcessInstanceNotFoundException;
import com.workflow.demo.exception.WorkflowNotFoundException;
import com.workflow.demo.repository.ProcessInstanceRepository;
import com.workflow.demo.repository.UserRepository;
import com.workflow.demo.repository.WorkflowRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowRepository workflowRepository;
    private final UserRepository userRepository;
    private final WorkflowValidationService workflowValidationService;
    private final ConditionEvaluationService conditionEvaluationService;
    private final NotificationService notificationService;

    public ProcessInstance initializeWorkflow(String processInstanceId, Map<String, Object> initialData, ObjectId createdByUserId) {
        ProcessInstance instance = processInstanceRepository.findById(toObjectId(processInstanceId))
            .orElseThrow(() -> new ProcessInstanceNotFoundException("No existe process_instance con id " + processInstanceId));

        Workflow workflow = workflowRepository.findById(instance.getWorkflowId())
            .orElseThrow(() -> new WorkflowNotFoundException("No existe workflow con id " + instance.getWorkflowId()));

        WorkflowNode startNode = workflow.getNodes().stream()
            .filter(n -> n.getTipo() == NodeType.inicio)
            .findFirst()
            .orElseThrow(() -> new InvalidWorkflowStateException("El workflow no tiene nodo de inicio configurado"));

        registrarHistorial(instance, HistoryEventType.tramite_creado, startNode.getId(), createdByUserId, "Trámite iniciado");
        instance.setEstado(ProcessStatus.en_proceso);

        Map<String, Object> contextoDatos = construirContexto(instance, initialData);
        
        // Usar snapshot si ya existe (para re-inicializaciones) o el workflow actual
        Object workflowSource = instance.getWorkflowSnapshot() != null ? instance.getWorkflowSnapshot() : workflow;
        activarNodoDestino(workflowSource, instance, startNode, contextoDatos, createdByUserId, null);

        return processInstanceRepository.save(instance);
    }

    public ProcessInstance completarActividad(
        String processInstanceId,
        String nodeId,
        String usuarioId,
        Map<String, Object> respuestaFormulario
    ) {
        ProcessInstance instance = processInstanceRepository
            .findById(toObjectId(processInstanceId))
            .orElseThrow(() -> new ProcessInstanceNotFoundException(
                "No existe process_instance con id " + processInstanceId));

        workflowValidationService.validateNodeIsActive(instance, nodeId);

        ProcessActivity activity = workflowValidationService.findActividadPendiente(instance, nodeId);
        User actor = workflowValidationService.validatePermisosYObtenerUsuario(instance, activity, usuarioId);

        Workflow workflow = workflowRepository
            .findById(instance.getWorkflowId())
            .orElseThrow(() -> new WorkflowNotFoundException(
                "No existe workflow con id " + instance.getWorkflowId()));

        // Usar snapshot como fuente de verdad si esta disponible (flujo ya en ejecucion)
        Object workflowSource = instance.getWorkflowSnapshot() != null ? instance.getWorkflowSnapshot() : workflow;
        WorkflowNode currentNode = getNodeFromWorkflowOrSnapshot(workflowSource, nodeId);
        if (currentNode == null) {
            throw new InvalidWorkflowStateException("Nodo no encontrado en la definicion del workflow: " + nodeId);
        }
        workflowValidationService.validateFormulario(currentNode, respuestaFormulario);

        cerrarActividad(activity, actor.getId(), respuestaFormulario);
        instance.getCurrentNodeIds().removeIf(nodeId::equals);
        if (instance.getEstado() == ProcessStatus.pendiente) {
            instance.setEstado(ProcessStatus.en_proceso);
        }

        registrarHistorial(instance, HistoryEventType.actividad_completada, nodeId, actor.getId(),
            "Actividad completada por usuario");

        if (!actor.getId().equals(instance.getClienteId())) {
            notificationService.notificarActividadCompletadaPorFuncionario(instance, currentNode.getNombre());
        }

        Map<String, Object> contextoDatos = construirContexto(instance, respuestaFormulario);
        WorkflowSnapshot snapshot = instance.getWorkflowSnapshot();
        List<WorkflowEdge> outgoing;
        List<Lane> lanes;

        if (snapshot != null) {
            outgoing = snapshot.getOutgoingEdges(nodeId);
            lanes = snapshot.getLanes();
        } else {
            outgoing = getOutgoingEdges(workflow, nodeId);
            lanes = workflow.getLanes();
        }

        for (WorkflowEdge edge : outgoing) {
            WorkflowNode destination;
            if (snapshot != null) {
                destination = snapshot.getNodeById(edge.getToNodeId());
            } else {
                destination = findNodeById(workflow, edge.getToNodeId());
            }
            
            if (destination == null) {
                throw new InvalidWorkflowStateException("Nodo destino no encontrado: " + edge.getToNodeId());
            }

            activarNodoDestino(snapshot != null ? snapshot : workflow, instance, destination, contextoDatos, actor.getId(), edge);
        }

        if (instance.getCurrentNodeIds().isEmpty() && instance.getEstado() == ProcessStatus.en_proceso) {
            instance.setEstado(ProcessStatus.finalizado);
            instance.setFinishedAt(Instant.now());
            registrarHistorial(instance, HistoryEventType.tramite_finalizado, nodeId, actor.getId(),
                "Finalizacion por consistencia: no quedan nodos activos");
            notificationService.notificarProcesoFinalizado(instance.getClienteId(), instance);
        }

        return processInstanceRepository.save(instance);
    }

    private void activarNodoDestino(
        Object workflowOrSnapshot, // Workflow or WorkflowSnapshot
        ProcessInstance instance,
        WorkflowNode destino,
        Map<String, Object> contextoDatos,
        ObjectId usuarioActorId,
        WorkflowEdge edgeLlegada
    ) {
        NodeType type = destino.getTipo();
        List<Lane> lanes = null;
        List<WorkflowEdge> allEdges = null;
        List<WorkflowNode> allNodes = null;

        if (workflowOrSnapshot instanceof WorkflowSnapshot s) {
            lanes = s.getLanes();
            allEdges = s.getEdges();
            allNodes = s.getNodes();
        } else if (workflowOrSnapshot instanceof Workflow w) {
            lanes = w.getLanes();
            allEdges = w.getEdges();
            allNodes = w.getNodes();
        }

        switch (type) {
            case actividad -> {
                // --- Deteccion implicita de JOIN ---
                // Si este nodo actividad tiene mas de 1 arista entrante, actuar como join:
                // esperar que TODOS los predecesores lleguen antes de crear la actividad.
                List<WorkflowEdge> incomingEdges = getIncomingEdgesFromWorkflowOrSnapshot(workflowOrSnapshot, destino.getId());
                if (incomingEdges.size() > 1) {
                    // Reutilizar la misma logica de join que paralelo_fin
                    resolverJoinImplicito(workflowOrSnapshot, instance, destino, contextoDatos, usuarioActorId, edgeLlegada, lanes, incomingEdges);
                } else {
                    // Nodo con una sola entrada: activacion directa
                    int iteracion = computeIteration(instance, destino.getId(), edgeLlegada);
                    crearActividadPendiente(instance, destino, iteracion, lanes);
                    if (!instance.getCurrentNodeIds().contains(destino.getId())) {
                        instance.getCurrentNodeIds().add(destino.getId());
                    }
                    registrarHistorial(instance, HistoryEventType.actividad_creada, destino.getId(), usuarioActorId,
                        "Actividad creada");

                    if (edgeLlegada != null && edgeLlegada.getTipo() == EdgeType.iterativo) {
                        registrarHistorial(instance, HistoryEventType.flujo_iterativo_retorno, destino.getId(), usuarioActorId,
                            "Retorno iterativo al nodo " + destino.getId());
                    }

                    notificarResponsablesDeActividad(instance, destino, usuarioActorId);
                }
            }
            case decision -> resolverDecision(workflowOrSnapshot, instance, destino, contextoDatos, usuarioActorId);
            case paralelo_inicio -> resolverParaleloInicio(workflowOrSnapshot, instance, destino, contextoDatos, usuarioActorId);
            case paralelo_fin -> resolverParaleloFin(workflowOrSnapshot, instance, destino, contextoDatos, usuarioActorId, edgeLlegada);
            case fin -> finalizarProceso(instance, usuarioActorId, destino.getId());
            case inicio -> {
                final List<WorkflowEdge> outgoingEdges = getEdgesFromWorkflowOrSnapshot(workflowOrSnapshot, destino.getId());
                for (WorkflowEdge edge : outgoingEdges) {
                    WorkflowNode next = getNodeFromWorkflowOrSnapshot(workflowOrSnapshot, edge.getToNodeId());
                    activarNodoDestino(workflowOrSnapshot, instance, next, contextoDatos, usuarioActorId, edge);
                }
            }
            default -> throw new InvalidWorkflowStateException("Tipo de nodo no soportado: " + type);
        }
    }

    private void resolverDecision(
        Object workflowOrSnapshot,
        ProcessInstance instance,
        WorkflowNode decisionNode,
        Map<String, Object> contextoDatos,
        ObjectId usuarioActorId
    ) {
        DecisionRule rule = decisionNode.getDecisionRule();
        if (rule == null) {
            throw new DecisionResolutionException(
                "El nodo de decision " + decisionNode.getId() + " no tiene regla de decision configurada");
        }

        boolean result = conditionEvaluationService.evaluateRule(rule, contextoDatos);
        String targetNodeId = result ? rule.getOnTrueDestinoNodeId() : rule.getOnFalseDestinoNodeId();

        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new DecisionResolutionException(
                "No hay destino configurado para el resultado " + result + " en nodo " + decisionNode.getId());
        }

        WorkflowNode targetNode = getNodeFromWorkflowOrSnapshot(workflowOrSnapshot, targetNodeId);
        WorkflowEdge selectedEdge = findEdgeFromWorkflowOrSnapshot(workflowOrSnapshot, decisionNode.getId(), targetNodeId);
        if (selectedEdge == null) {
            throw new DecisionResolutionException(
                "No existe edge entre decision " + decisionNode.getId() + " y destino " + targetNodeId);
        }
        activarNodoDestino(workflowOrSnapshot, instance, targetNode, contextoDatos, usuarioActorId, selectedEdge);
    }

    private void resolverParaleloInicio(
        Object workflowOrSnapshot,
        ProcessInstance instance,
        WorkflowNode parallelStartNode,
        Map<String, Object> contextoDatos,
        ObjectId usuarioActorId
    ) {
        List<WorkflowEdge> outgoing = getEdgesFromWorkflowOrSnapshot(workflowOrSnapshot, parallelStartNode.getId());
        if (outgoing.size() < 2) {
            throw new InvalidWorkflowStateException(
                "Un nodo paralelo_inicio debe tener al menos dos salidas: " + parallelStartNode.getId());
        }

        HistoryEvent event = new HistoryEvent();
        event.setTipo(HistoryEventType.flujo_paralelo_activado);
        event.setNodeId(parallelStartNode.getId());
        event.setUsuarioId(usuarioActorId);
        event.setFecha(Instant.now());
        event.setNodeIds(outgoing.stream().map(WorkflowEdge::getToNodeId).toList());
        event.setDetalle("Ramas paralelas activadas");
        instance.getHistorial().add(event);

        for (WorkflowEdge edge : outgoing) {
            WorkflowNode destination = getNodeFromWorkflowOrSnapshot(workflowOrSnapshot, edge.getToNodeId());
            activarNodoDestino(workflowOrSnapshot, instance, destination, contextoDatos, usuarioActorId, edge);
        }
    }

    private void resolverParaleloFin(
        Object workflowOrSnapshot,
        ProcessInstance instance,
        WorkflowNode parallelJoinNode,
        Map<String, Object> contextoDatos,
        ObjectId usuarioActorId,
        WorkflowEdge edgeLlegada
    ) {
        String joinNodeId = parallelJoinNode.getId();
        String predecessorNodeId = edgeLlegada != null ? edgeLlegada.getFromNodeId() : null;

        Set<String> predecessors = getIncomingEdgesFromWorkflowOrSnapshot(workflowOrSnapshot, joinNodeId)
            .stream()
            .map(WorkflowEdge::getFromNodeId)
            .collect(HashSet::new, Set::add, Set::addAll);

        Instant lastResolvedAt = getLastJoinResolvedAt(instance, joinNodeId);
        registrarLlegadaAlJoin(instance, joinNodeId, predecessorNodeId, usuarioActorId, lastResolvedAt);

        Set<String> predecessorsArrived = obtenerPredecesoresQueLlegaronAlJoin(instance, joinNodeId, lastResolvedAt);
        boolean allPredecessorsCompleted = predecessors.stream().allMatch(predecessorsArrived::contains);

        if (!allPredecessorsCompleted) {
            return;
        }

        marcarJoinComoResuelto(instance, joinNodeId, usuarioActorId);

        List<WorkflowEdge> outgoing = getEdgesFromWorkflowOrSnapshot(workflowOrSnapshot, joinNodeId);
        for (WorkflowEdge edge : outgoing) {
            WorkflowNode destination = getNodeFromWorkflowOrSnapshot(workflowOrSnapshot, edge.getToNodeId());
            activarNodoDestino(workflowOrSnapshot, instance, destination, contextoDatos, usuarioActorId, edge);
        }
    }

    /**
     * Join implicito: cualquier nodo actividad con N > 1 aristas entrantes actua como join gate.
     * Acumula llegadas en historial (mismo mecanismo que paralelo_fin) y solo crea la actividad
     * cuando TODOS los predecesores han llegado.
     *
     * Esto permite topologias simples sin necesitar nodos paralelo_inicio / paralelo_fin:
     *   nodo1 → nodo2 ─┐
     *   nodo1 → nodo3 ─┤→ nodo4 (solo se activa cuando nodo2 Y nodo3 completaron)
     */
    private void resolverJoinImplicito(
        Object workflowOrSnapshot,
        ProcessInstance instance,
        WorkflowNode joinNode,
        Map<String, Object> contextoDatos,
        ObjectId usuarioActorId,
        WorkflowEdge edgeLlegada,
        List<Lane> lanes,
        List<WorkflowEdge> incomingEdges
    ) {
        String joinNodeId = joinNode.getId();
        String predecessorNodeId = edgeLlegada != null ? edgeLlegada.getFromNodeId() : null;

        Set<String> allPredecessors = incomingEdges.stream()
            .map(WorkflowEdge::getFromNodeId)
            .collect(HashSet::new, Set::add, Set::addAll);

        Instant lastResolvedAt = getLastJoinResolvedAt(instance, joinNodeId);
        registrarLlegadaAlJoin(instance, joinNodeId, predecessorNodeId, usuarioActorId, lastResolvedAt);

        Set<String> predecessorsArrived = obtenerPredecesoresQueLlegaronAlJoin(instance, joinNodeId, lastResolvedAt);
        boolean allArrived = allPredecessors.stream().allMatch(predecessorsArrived::contains);

        if (!allArrived) {
            // Todavia esperando ramas pendientes — no crear actividad aun
            return;
        }

        // Todos los predecesores llegaron → marcar join resuelto y crear la actividad
        marcarJoinComoResuelto(instance, joinNodeId, usuarioActorId);

        int iteracion = computeIteration(instance, joinNodeId, edgeLlegada);
        crearActividadPendiente(instance, joinNode, iteracion, lanes);
        if (!instance.getCurrentNodeIds().contains(joinNodeId)) {
            instance.getCurrentNodeIds().add(joinNodeId);
        }
        registrarHistorial(instance, HistoryEventType.actividad_creada, joinNodeId, usuarioActorId,
            "Actividad creada tras join implicito (todos los predecesores completados)");
        notificarResponsablesDeActividad(instance, joinNode, usuarioActorId);
    }

    private void crearActividadPendiente(ProcessInstance instance, WorkflowNode node, int iteracion, List<Lane> lanes) {
        if (node.getResponsableTipo() == ResponsableTipo.usuario && node.getResponsableUsuarioId() == null) {
            throw new InvalidWorkflowStateException(
                "El nodo actividad " + node.getId() + " requiere responsableUsuarioId");
        }

        ObjectId departmentId = node.getDepartmentId();
        // Si no tiene departmentId pero es de tipo departamento, intentar resolverlo por lane
        if (departmentId == null && node.getResponsableTipo() == ResponsableTipo.departamento && lanes != null) {
            departmentId = lanes.stream()
                .filter(l -> l.getId().equals(node.getLaneId()))
                .map(Lane::getDepartmentId)
                .findFirst()
                .orElse(null);
            
            // Si lo encontramos, lo actualizamos en el nodo para futuras referencias (opcional pero util)
            if (departmentId != null) {
                log.info("ℹ️ Resolviendo departmentId {} desde lane {} para nodo {}", departmentId, node.getLaneId(), node.getId());
                node.setDepartmentId(departmentId);
            } else {
                log.warn("⚠️ No se pudo resolver departmentId para el nodo {} de tipo departamento", node.getId());
            }
        }

        if (node.getResponsableTipo() == ResponsableTipo.departamento && departmentId == null) {
            throw new InvalidWorkflowStateException(
                "El nodo actividad " + node.getId() + " requiere departmentId (no se encontro ni en el nodo ni en la lane)");
        }

        ProcessActivity activity = new ProcessActivity();
        activity.setActividadId(UUID.randomUUID().toString());
        activity.setNodeId(node.getId());
        activity.setNombre(node.getNombre());
        activity.setResponsableTipo(node.getResponsableTipo());
        activity.setDepartmentId(departmentId != null ? departmentId : node.getDepartmentId());
        
        // Asignar SLA si existe
        if (node.getSlaMinutos() != null) {
            activity.setFechaFin(Instant.now().plus(node.getSlaMinutos(), java.time.temporal.ChronoUnit.MINUTES));
        }
        if (node.getResponsableTipo() == ResponsableTipo.usuario) {
            activity.setUsuarioId(node.getResponsableUsuarioId());
        }
        activity.setEstado(ActivityStatus.pendiente);
        activity.setIteracion(iteracion);
        activity.setFechaInicio(null);
        activity.setFechaFin(null);
        activity.setRespuestaFormulario(new HashMap<>());

        instance.getActividades().add(activity);
    }

    private void finalizarProceso(ProcessInstance instance, ObjectId usuarioActorId, String nodeId) {
        instance.setEstado(ProcessStatus.finalizado);
        instance.setFinishedAt(Instant.now());
        instance.getCurrentNodeIds().clear();

        registrarHistorial(instance, HistoryEventType.tramite_finalizado, nodeId, usuarioActorId,
            "Proceso finalizado");

        notificationService.notificarProcesoFinalizado(instance.getClienteId(), instance);
    }

    private void cerrarActividad(ProcessActivity activity, ObjectId usuarioId, Map<String, Object> respuestaFormulario) {
        activity.setEstado(ActivityStatus.completada);
        activity.setFechaFin(Instant.now());
        activity.setUsuarioId(usuarioId);
        activity.setRespuestaFormulario(respuestaFormulario == null ? new HashMap<>() : new HashMap<>(respuestaFormulario));
    }

    /**
     * Construye el contexto de datos para la evaluacion de reglas de decision.
     * Estrategia: para cada nodo, usa solo la respuesta de la ultima actividad completada
     * (la de mayor fechaFin). Esto garantiza que en flujos iterativos (bucles), el nodo
     * decision siempre evalua el formulario MAS RECIENTE enviado para cada nodo.
     */
    private Map<String, Object> construirContexto(ProcessInstance instance, Map<String, Object> respuestaFormulario) {
        Map<String, Object> context = new HashMap<>();

        // Agrupar actividades completadas por nodeId y conservar solo la mas reciente
        instance.getActividades().stream()
            .filter(a -> a.getEstado() == ActivityStatus.completada)
            .filter(a -> a.getRespuestaFormulario() != null && !a.getRespuestaFormulario().isEmpty())
            .collect(java.util.stream.Collectors.toMap(
                ProcessActivity::getNodeId,
                a -> a,
                // En caso de colision (mismo nodeId), conservar la actividad con fechaFin mas reciente
                (a1, a2) -> {
                    Instant t1 = a1.getFechaFin() != null ? a1.getFechaFin() : Instant.MIN;
                    Instant t2 = a2.getFechaFin() != null ? a2.getFechaFin() : Instant.MIN;
                    return t1.isAfter(t2) ? a1 : a2;
                }
            ))
            .values()
            .forEach(a -> context.putAll(a.getRespuestaFormulario()));

        if (instance.getDatosResumen() != null) {
            context.put("titulo", instance.getDatosResumen().getTitulo());
            context.put("descripcionCliente", instance.getDatosResumen().getDescripcionCliente());
            context.put("ultimaActualizacionVisible", instance.getDatosResumen().getUltimaActualizacionVisible());
        }
        context.put("processCodigo", instance.getCodigo());
        context.put("processEstado", instance.getEstado() != null ? instance.getEstado().name() : null);

        // La respuesta del formulario actual (el recien enviado) tiene maxima prioridad
        if (respuestaFormulario != null) {
            context.putAll(respuestaFormulario);
        }

        return context;
    }

    private void registrarHistorial(
        ProcessInstance instance,
        HistoryEventType type,
        String nodeId,
        ObjectId usuarioId,
        String detalle
    ) {
        HistoryEvent event = new HistoryEvent();
        event.setTipo(type);
        event.setNodeId(nodeId);
        event.setUsuarioId(usuarioId);
        event.setFecha(Instant.now());
        event.setDetalle(detalle);
        instance.getHistorial().add(event);
    }

    private boolean isNodeCompleted(ProcessInstance instance, String nodeId) {
        return instance
            .getActividades()
            .stream()
            .filter(a -> nodeId.equals(a.getNodeId()))
            .max(Comparator.comparing(a -> a.getFechaFin() != null ? a.getFechaFin() : Instant.MIN))
            .map(a -> a.getEstado() == ActivityStatus.completada)
            .orElse(false);
    }

    private void notificarResponsablesDeActividad(ProcessInstance instance, WorkflowNode node, ObjectId usuarioActorId) {
        if (node.getResponsableTipo() == ResponsableTipo.cliente) {
            notificationService.notificarActividadAsignada(instance.getClienteId(), instance, node);
            registrarHistorial(instance, HistoryEventType.notificacion_generada, node.getId(), usuarioActorId,
                "Notificacion generada para cliente");
            return;
        }

        if (node.getResponsableTipo() == ResponsableTipo.usuario && node.getResponsableUsuarioId() != null) {
            notificationService.notificarActividadAsignada(node.getResponsableUsuarioId(), instance, node);
            registrarHistorial(instance, HistoryEventType.notificacion_generada, node.getId(), usuarioActorId,
                "Notificacion generada para usuario responsable");
            return;
        }

        if (node.getResponsableTipo() == ResponsableTipo.departamento) {
            ObjectId deptId = node.getDepartmentId();
            
            // Si es null, intentar resolverlo (igual que en crearActividadPendiente)
            if (deptId == null && instance.getWorkflowSnapshot() != null) {
                deptId = instance.getWorkflowSnapshot().getLanes().stream()
                    .filter(l -> l.getId().equals(node.getLaneId()))
                    .map(Lane::getDepartmentId)
                    .findFirst()
                    .orElse(null);
            }
            
            if (deptId != null) {
                userRepository.findByDepartmentId(deptId).stream()
                    .filter(User::isActivo)
                    .forEach(user -> notificationService.notificarActividadAsignada(user.getId(), instance, node));

                registrarHistorial(instance, HistoryEventType.notificacion_generada, node.getId(), usuarioActorId,
                    "Notificacion generada para departamento responsable");
            } else {
                log.warn("⚠️ No se pudo notificar a departamento: departmentId es null para nodo {}", node.getId());
            }
        }
    }

    private Instant getLastJoinResolvedAt(ProcessInstance instance, String joinNodeId) {
        return instance.getHistorial().stream()
            .filter(e -> e.getTipo() == HistoryEventType.join_resuelto)
            .filter(e -> joinNodeId.equals(e.getNodeId()))
            .map(HistoryEvent::getFecha)
            .filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(Instant.MIN);
    }

    private void registrarLlegadaAlJoin(
        ProcessInstance instance,
        String joinNodeId,
        String predecessorNodeId,
        ObjectId usuarioActorId,
        Instant lastResolvedAt
    ) {
        if (predecessorNodeId == null) {
            return;
        }

        Set<String> alreadyArrived = obtenerPredecesoresQueLlegaronAlJoin(instance, joinNodeId, lastResolvedAt);
        if (alreadyArrived.contains(predecessorNodeId)) {
            return;
        }

        HistoryEvent event = new HistoryEvent();
        event.setTipo(HistoryEventType.join_branch_arrived);
        event.setNodeId(joinNodeId);
        event.setUsuarioId(usuarioActorId);
        event.setFecha(Instant.now());
        event.setNodeIds(List.of(predecessorNodeId));
        event.setDetalle("La rama " + predecessorNodeId + " llego al join " + joinNodeId);
        instance.getHistorial().add(event);
    }

    private Set<String> obtenerPredecesoresQueLlegaronAlJoin(
        ProcessInstance instance,
        String joinNodeId,
        Instant lastResolvedAt
    ) {
        return instance.getHistorial().stream()
            .filter(e -> e.getTipo() == HistoryEventType.join_branch_arrived)
            .filter(e -> joinNodeId.equals(e.getNodeId()))
            .filter(e -> e.getFecha() != null && e.getFecha().isAfter(lastResolvedAt))
            .flatMap(e -> e.getNodeIds().stream())
            .collect(HashSet::new, Set::add, Set::addAll);
    }

    private void marcarJoinComoResuelto(ProcessInstance instance, String joinNodeId, ObjectId usuarioActorId) {
        HistoryEvent event = new HistoryEvent();
        event.setTipo(HistoryEventType.join_resuelto);
        event.setNodeId(joinNodeId);
        event.setUsuarioId(usuarioActorId);
        event.setFecha(Instant.now());
        event.setDetalle("Join paralelo resuelto");
        instance.getHistorial().add(event);
    }

    /**
     * Calcula el numero de iteracion para un nodo que esta siendo activado.
     * Si el edge es de tipo iterativo, incrementa. Si el edge apunta a un nodo
     * que ya tiene actividades (sin importar el tipo de edge) tambien lo detecta
     * como un bucle y lo trata como iterativo automaticamente.
     */
    private int computeIteration(ProcessInstance instance, String nodeId, WorkflowEdge edgeLlegada) {
        long previousExecutions = instance.getActividades().stream()
            .filter(a -> nodeId.equals(a.getNodeId()))
            .count();

        if (previousExecutions == 0) {
            return 1;
        }

        // Si ya hay ejecuciones previas del mismo nodo, es un ciclo/bucle independientemente
        // de si el edge esta marcado como iterativo o no
        boolean isLoop = (edgeLlegada != null && edgeLlegada.getTipo() == EdgeType.iterativo)
            || previousExecutions > 0;

        return isLoop ? (int) previousExecutions + 1 : 1;
    }

    private WorkflowEdge findEdge(Workflow workflow, String fromNodeId, String toNodeId) {
        return workflow
            .getEdges()
            .stream()
            .filter(e -> fromNodeId.equals(e.getFromNodeId()) && toNodeId.equals(e.getToNodeId()))
            .findFirst()
            .orElse(null);
    }

    private List<WorkflowEdge> getOutgoingEdges(Workflow workflow, String nodeId) {
        if (workflow.getEdges() == null) {
            return List.of();
        }
        return workflow.getEdges().stream().filter(e -> nodeId.equals(e.getFromNodeId())).toList();
    }

    private List<WorkflowEdge> getIncomingEdges(Workflow workflow, String nodeId) {
        if (workflow.getEdges() == null) {
            return List.of();
        }
        return workflow.getEdges().stream().filter(e -> nodeId.equals(e.getToNodeId())).toList();
    }

    private WorkflowNode findNodeById(Workflow workflow, String nodeId) {
        return workflow
            .getNodes()
            .stream()
            .filter(node -> nodeId.equals(node.getId()))
            .findFirst()
            .orElseThrow(() -> new InvalidWorkflowStateException("Nodo no encontrado en workflow: " + nodeId));
    }

    private WorkflowNode getNodeFromWorkflowOrSnapshot(Object source, String nodeId) {
        if (source instanceof WorkflowSnapshot s) {
            return s.getNodeById(nodeId);
        } else if (source instanceof Workflow w) {
            return findNodeById(w, nodeId);
        }
        return null;
    }

    private List<WorkflowEdge> getEdgesFromWorkflowOrSnapshot(Object source, String nodeId) {
        if (source instanceof WorkflowSnapshot s) {
            return s.getOutgoingEdges(nodeId);
        } else if (source instanceof Workflow w) {
            return getOutgoingEdges(w, nodeId);
        }
        return List.of();
    }

    private List<WorkflowEdge> getIncomingEdgesFromWorkflowOrSnapshot(Object source, String nodeId) {
        if (source instanceof WorkflowSnapshot s) {
            return s.getIncomingEdges(nodeId);
        } else if (source instanceof Workflow w) {
            return getIncomingEdges(w, nodeId);
        }
        return List.of();
    }

    private WorkflowEdge findEdgeFromWorkflowOrSnapshot(Object source, String from, String to) {
        if (source instanceof WorkflowSnapshot s) {
            return s.getEdges().stream()
                .filter(e -> from.equals(e.getFromNodeId()) && to.equals(e.getToNodeId()))
                .findFirst()
                .orElse(null);
        } else if (source instanceof Workflow w) {
            return findEdge(w, from, to);
        }
        return null;
    }

    private ObjectId toObjectId(String value) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidWorkflowStateException("Id invalido: " + value);
        }
    }
}
