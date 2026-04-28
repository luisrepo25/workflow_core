package com.workflow.demo.workflowdesign.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.WorkflowStatus;
import com.workflow.demo.repository.WorkflowRepository;
import com.workflow.demo.workflowdesign.dto.WorkflowChangeMessage;
import com.workflow.demo.workflowdesign.dto.WorkflowEditingState;
import com.workflow.demo.workflowdesign.dto.WorkflowEditingState.ActiveEditor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para mantener el estado de edicion colaborativa en memoria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCollaborictionService {

    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    // workflowId -> EditingState
    private final Map<String, WorkflowEditingState> editingStates = new ConcurrentHashMap<>();

    // workflowId -> lista de cambios recientes
    private final Map<String, List<WorkflowChangeMessage>> changeHistory = new ConcurrentHashMap<>();

    // workflowId -> laneId por nodeId para validaciones de borrado de lane
    private final Map<String, Map<String, String>> nodeLaneIndex = new ConcurrentHashMap<>();

    // workflowId -> edgeId -> edge (permite borrar por edgeId aunque el modelo persistido no tenga id)
    private final Map<String, Map<String, WorkflowEdge>> edgeIndex = new ConcurrentHashMap<>();

    // workflowId -> messageIds procesados para idempotencia
    private final Map<String, Set<String>> processedMessageIds = new ConcurrentHashMap<>();

    // workflowId -> lock de mutaciones (evita condiciones de carrera entre eventos simultaneos)
    private final Map<String, ReentrantLock> workflowMutationLocks = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY_SIZE = 1000;
    private static final long SESSION_TIMEOUT = 5 * 60 * 1000; // 5 minutos

    /**
     * Registra un usuario como activo en la edicion.
     */
    public void addActiveEditor(String workflowId, String userId, String userName, String email) {
        WorkflowEditingState state = editingStates.computeIfAbsent(workflowId, this::buildStateFromWorkflow);

        boolean exists = state.getActiveEditors().stream().anyMatch(editor -> editor.getUserId().equals(userId));

        if (!exists) {
            ActiveEditor editor = ActiveEditor.builder()
                .userId(userId)
                .userName(userName)
                .email(email)
                .connectedAt(System.currentTimeMillis())
                .build();
            state.getActiveEditors().add(editor);
            log.info("Usuario {} conectado a workflow {}", userName, workflowId);
        }
    }

    /**
     * Remueve un usuario del estado de edicion.
     */
    public void removeActiveEditor(String workflowId, String userId) {
        WorkflowEditingState state = editingStates.get(workflowId);
        if (state != null) {
            state.getActiveEditors().removeIf(editor -> editor.getUserId().equals(userId));

            if (state.getActiveEditors().isEmpty()) {
                editingStates.remove(workflowId);
            }
            log.info("Usuario {} desconectado de workflow {}", userId, workflowId);
        }
    }

    /**
     * Registra un cambio en el historial y aplica cambios al estado de sesion.
     */
    public boolean recordChange(WorkflowChangeMessage change) {
        String workflowId = change.getWorkflowId();

        ReentrantLock workflowLock = workflowMutationLocks.computeIfAbsent(workflowId, k -> new ReentrantLock());
        workflowLock.lock();
        try {
            if (isDuplicatedMessage(workflowId, change.getMessageId())) {
                log.debug("Mensaje duplicado ignorado en workflow {}: {}", workflowId, change.getMessageId());
                return false;
            }

            boolean skipValidation = isDraftWorkflow(workflowId);
            WorkflowEditingState state = editingStates.computeIfAbsent(workflowId, this::buildStateFromWorkflow);
            applyChangeToSessionState(workflowId, state, change, skipValidation);
            persistEditingState(workflowId, state);

            List<WorkflowChangeMessage> history = changeHistory.computeIfAbsent(workflowId, k -> new ArrayList<>());
            history.add(change);

            if (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }

            state.getPendingChanges().add(change);
            state.setLastModified(System.currentTimeMillis());
            log.debug("Estado workflow {} tras {}: nodes={}, edges={}, lanes={}",
                workflowId,
                change.getAction(),
                nodesOf(state).size(),
                edgesOf(state).size(),
                lanesOf(state).size());

            return true;
        } finally {
            workflowLock.unlock();
        }
    }

    /**
     * Obtiene el estado actual de edicion.
     */
    public WorkflowEditingState getEditingState(String workflowId) {
        return editingStates.computeIfAbsent(workflowId, this::buildStateFromWorkflow);
    }

    /**
     * Limpia los cambios pendientes despues de guardar.
     */
    public void clearPendingChanges(String workflowId) {
        WorkflowEditingState state = editingStates.get(workflowId);
        if (state != null) {
            state.getPendingChanges().clear();
        }
    }

    /**
     * Obtiene el historial de cambios.
     */
    public List<WorkflowChangeMessage> getChangeHistory(String workflowId) {
        return changeHistory.getOrDefault(workflowId, new ArrayList<>());
    }

    /**
     * Limpia sesiones expiradas.
     */
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();

        editingStates.forEach((workflowId, state) -> {
            state.getActiveEditors().removeIf(editor -> (now - editor.getConnectedAt()) > SESSION_TIMEOUT);

            if (state.getActiveEditors().isEmpty()) {
                editingStates.remove(workflowId);
                changeHistory.remove(workflowId);
                nodeLaneIndex.remove(workflowId);
                edgeIndex.remove(workflowId);
                processedMessageIds.remove(workflowId);
                workflowMutationLocks.remove(workflowId);
            }
        });
    }

    /**
     * Cuenta usuarios activos en un workflow.
     */
    public int getActiveEditorCount(String workflowId) {
        WorkflowEditingState state = editingStates.get(workflowId);
        return state != null ? state.getActiveEditors().size() : 0;
    }

    /**
     * Obtiene lista de usuarios activos.
     */
    public List<ActiveEditor> getActiveEditors(String workflowId) {
        WorkflowEditingState state = editingStates.get(workflowId);
        return state != null ? new ArrayList<>(state.getActiveEditors()) : new ArrayList<>();
    }

    /**
     * Actualiza la seleccion/cursor de un usuario.
     */
    public void updateEditorSelection(String workflowId, String userId, String selectedElement, String cursorPosition) {
        WorkflowEditingState state = editingStates.get(workflowId);
        if (state != null) {
            state.getActiveEditors().stream()
                .filter(editor -> editor.getUserId().equals(userId))
                .findFirst()
                .ifPresent(editor -> {
                    editor.setSelectedElement(selectedElement);
                    editor.setCursorPosition(cursorPosition);
                });
        }
    }

    private WorkflowEditingState buildStateFromWorkflow(String workflowId) {
        WorkflowEditingState state = new WorkflowEditingState();
        state.setWorkflowId(workflowId);
        state.setActiveEditors(new ArrayList<>());
        state.setPendingChanges(new ArrayList<>());
        state.setLockedAt(System.currentTimeMillis());
        state.setLastModified(System.currentTimeMillis());
        state.setLanes(new ArrayList<Lane>());
        state.setNodes(new ArrayList<WorkflowNode>());
        state.setEdges(new ArrayList<WorkflowEdge>());

        Workflow workflow = workflowRepository.findById(toObjectId(workflowId)).orElse(null);
        if (workflow == null) {
            nodeLaneIndex.put(workflowId, new ConcurrentHashMap<>());
            return state;
        }

        List<Lane> lanes = workflow.getLanes() != null ? new ArrayList<>(workflow.getLanes()) : new ArrayList<>();
        List<WorkflowNode> nodes = workflow.getNodes() != null ? new ArrayList<>(workflow.getNodes()) : new ArrayList<>();
        List<WorkflowEdge> edges = workflow.getEdges() != null ? new ArrayList<>(workflow.getEdges()) : new ArrayList<>();

        state.setLanes(lanes);
        state.setNodes(nodes);
        state.setEdges(edges);

        // Indexar edges existentes con key canonica para permitir borrado por edgeId cuando coincide.
        Map<String, WorkflowEdge> indexedEdges = new ConcurrentHashMap<>();
        for (WorkflowEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String canonicalId = buildCanonicalEdgeId(edge.getFromNodeId(), edge.getToNodeId());
            if (canonicalId != null) {
                indexedEdges.put(canonicalId, edge);
            }
        }
        edgeIndex.put(workflowId, indexedEdges);

        Map<String, String> laneByNode = nodes.stream()
            .filter(n -> n.getId() != null)
            .collect(Collectors.toMap(WorkflowNode::getId, WorkflowNode::getLaneId, (a, b) -> b, ConcurrentHashMap::new));
        nodeLaneIndex.put(workflowId, laneByNode);

        return state;
    }

    private boolean isDuplicatedMessage(String workflowId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }

        Set<String> messageIds = processedMessageIds.computeIfAbsent(workflowId, k -> ConcurrentHashMap.newKeySet());
        return !messageIds.add(messageId);
    }

    private void applyChangeToSessionState(
            String workflowId,
            WorkflowEditingState state,
            WorkflowChangeMessage change,
            boolean skipValidation) {
        if (change.getAction() == null) {
            return;
        }

        switch (change.getAction()) {
            case "lane_added" -> applyLaneAddOrUpdate(state, workflowId, change, true, skipValidation);
            case "lane_updated" -> applyLaneAddOrUpdate(state, workflowId, change, false, skipValidation);
            case "lane_deleted" -> applyLaneDelete(state, workflowId, change, skipValidation);
            case "node_added" -> applyNodeAddOrUpdate(state, workflowId, change, true, skipValidation);
            case "node_updated" -> applyNodeAddOrUpdate(state, workflowId, change, false, skipValidation);
            case "node_deleted" -> applyNodeDelete(state, workflowId, change, skipValidation);
            case "edge_added" -> applyEdgeAddOrUpdate(state, change, skipValidation);
            case "edge_deleted" -> applyEdgeDelete(state, change, skipValidation);
            default -> {
                // Sin cambios de estado adicionales para otras acciones.
            }
        }
    }

    private void applyLaneAddOrUpdate(
            WorkflowEditingState state,
            String workflowId,
            WorkflowChangeMessage change,
            boolean addSemantics,
            boolean skipValidation) {
        
        Object data = change.getData();
        String laneId = extractLaneId(change); // Helper que ya debe existir o crearemos

        if (laneId == null || laneId.isBlank()) {
            if (skipValidation) return;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lane debe tener id");
        }

        List<Lane> lanes = lanesOf(state);
        int idx = findLaneIndex(lanes, laneId);
        Lane laneToProcess;

        if (idx >= 0) {
            // MERGE
            laneToProcess = lanes.get(idx);
            ObjectId oldDeptId = laneToProcess.getDepartmentId();
            updateLaneFromData(laneToProcess, data);
            
            // Propagar cambio de departamento a los nodos si cambió
            if (!Objects.equals(oldDeptId, laneToProcess.getDepartmentId())) {
                propagateLaneDepartmentToNodes(state, laneToProcess.getId(), laneToProcess.getDepartmentId());
            }
            change.setAction("lane_updated");
        } else {
            // CREATE
            laneToProcess = asLane(data);
            if (laneToProcess == null) {
                if (skipValidation) return;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de lane invalidos");
            }
            lanes.add(laneToProcess);
        }

        normalizeLaneOrder(lanes);
        state.setLanes(lanes);
        state.setLastModified(System.currentTimeMillis());
    }

    private void propagateLaneDepartmentToNodes(WorkflowEditingState state, String laneId, ObjectId departmentId) {
        if (laneId == null) return;
        List<WorkflowNode> nodes = nodesOf(state);
        boolean changed = false;
        for (WorkflowNode node : nodes) {
            if (laneId.equals(node.getLaneId())) {
                if (!Objects.equals(node.getDepartmentId(), departmentId)) {
                    node.setDepartmentId(departmentId);
                    changed = true;
                    log.info("Propagando departmentId {} al nodo {} por cambio en lane {}", 
                        departmentId, node.getId(), laneId);
                }
            }
        }
        if (changed) state.setNodes(nodes);
    }

    private void updateLaneFromData(Lane existingLane, Object data) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(data);
            objectMapper.readerForUpdating(existingLane).readValue(jsonBytes);
        } catch (Exception e) {
            log.error("Error merging lane data for {}: {}", existingLane.getId(), e.getMessage());
        }
    }

    private void applyLaneDelete(
            WorkflowEditingState state,
            String workflowId,
            WorkflowChangeMessage change,
            boolean skipValidation) {
        String laneId = extractLaneId(change);
        if (laneId == null || laneId.isBlank()) {
            if (skipValidation) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "laneId es requerido para lane_deleted");
        }

        Map<String, String> laneByNode = nodeLaneIndex.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>());
        boolean hasEmbeddedNodes = laneByNode.values().stream().anyMatch(l -> laneId.equals(l));
        if (hasEmbeddedNodes && !skipValidation) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede borrar lane con nodos embebidos. Reasigne o elimine nodos primero");
        }

        if (hasEmbeddedNodes && skipValidation) {
            laneByNode.entrySet().removeIf(entry -> laneId.equals(entry.getValue()));
        }

        List<Lane> lanes = lanesOf(state);
        int idx = findLaneIndex(lanes, laneId);
        if (idx < 0) {
            if (skipValidation) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lane no existe: " + laneId);
        }

        lanes.remove(idx);
        normalizeLaneOrder(lanes);
        state.setLanes(lanes);
        state.setLastModified(System.currentTimeMillis());
        change.setLaneId(laneId);
    }

    private void applyNodeAddOrUpdate(
            WorkflowEditingState state,
            String workflowId,
            WorkflowChangeMessage change,
            boolean addSemantics,
            boolean skipValidation) {
        
        Object data = change.getData();
        String nodeId = change.getNodeId();
        if ((nodeId == null || nodeId.isBlank()) && data instanceof Map<?, ?> map) {
            Object idVal = map.get("id");
            if (idVal != null) nodeId = String.valueOf(idVal);
        }

        if (nodeId == null || nodeId.isBlank()) {
            if (skipValidation) return;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nodo invalido o sin id");
        }

        List<WorkflowNode> nodes = nodesOf(state);
        int idx = findNodeIndex(nodes, nodeId);
        WorkflowNode nodeToProcess;

        if (idx >= 0) {
            // MERGE: Actualizar solo los campos que vienen en el mensaje
            nodeToProcess = nodes.get(idx);
            updateNodeFromData(nodeToProcess, data);
            change.setAction("node_updated");
        } else {
            // CREATE: Convertir todo el objeto
            nodeToProcess = asNode(data);
            if (nodeToProcess == null) {
                if (skipValidation) return;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de nodo invalidos");
            }
            nodes.add(nodeToProcess);
        }

        state.setNodes(nodes);
        state.setLastModified(System.currentTimeMillis());
        change.setNodeId(nodeToProcess.getId());

        // REGLA CRITICA: Siempre asegurar que el nodo tenga el departmentId correcto basado en su lane
        if (nodeToProcess.getResponsableTipo() == com.workflow.demo.domain.enums.ResponsableTipo.departamento || nodeToProcess.getLaneId() != null) {
            String laneId = nodeToProcess.getLaneId();
            if (laneId != null) {
                lanesOf(state).stream()
                    .filter(l -> laneId.equals(l.getId()))
                    .map(Lane::getDepartmentId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(deptId -> {
                        if (!deptId.equals(nodeToProcess.getDepartmentId())) {
                            log.info("🔄 Sincronizando departmentId {} para el nodo {} (antes: {})", 
                                deptId, nodeToProcess.getId(), nodeToProcess.getDepartmentId());
                            nodeToProcess.setDepartmentId(deptId);
                        }
                    });
            }
        }

        updateNodeLaneIndex(workflowId, change);
    }

    private void updateNodeFromData(WorkflowNode existingNode, Object data) {
        try {
            // Usamos el readerForUpdating de Jackson para hacer un merge parcial sobre el objeto existente
            byte[] jsonBytes = objectMapper.writeValueAsBytes(data);
            objectMapper.readerForUpdating(existingNode).readValue(jsonBytes);
        } catch (Exception e) {
            log.error("Error merging node data for {}: {}", existingNode.getId(), e.getMessage());
        }
    }

    private void applyNodeDelete(
            WorkflowEditingState state,
            String workflowId,
            WorkflowChangeMessage change,
            boolean skipValidation) {
        String nodeId = change.getNodeId();
        if ((nodeId == null || nodeId.isBlank()) && change.getData() instanceof Map<?, ?> map) {
            Object value = map.get("id");
            if (value != null) {
                nodeId = String.valueOf(value);
            }
        }

        if (nodeId == null || nodeId.isBlank()) {
            if (skipValidation) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nodeId es requerido para node_deleted");
        }

        final String resolvedNodeId = nodeId;

        List<WorkflowNode> nodes = nodesOf(state);
        int nodesBefore = nodes.size();
        boolean removed = nodes.removeIf(node -> node != null && resolvedNodeId.equals(node.getId()));
        if (!removed && !skipValidation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nodo no existe: " + resolvedNodeId);
        }

        List<WorkflowEdge> edges = edgesOf(state);
        edges.removeIf(edge -> edge != null
            && (resolvedNodeId.equals(edge.getFromNodeId()) || resolvedNodeId.equals(edge.getToNodeId())));

        state.setNodes(nodes);
        state.setEdges(edges);
        state.setLastModified(System.currentTimeMillis());

        change.setNodeId(resolvedNodeId);
        removeNodeFromIndex(workflowId, resolvedNodeId);
        log.info("node_deleted aplicado en workflow {} para nodeId {}: nodesAntes={}, nodesDespues={}",
            workflowId, resolvedNodeId, nodesBefore, nodes.size());
    }

    private void applyEdgeAddOrUpdate(WorkflowEditingState state, WorkflowChangeMessage change, boolean skipValidation) {
        WorkflowEdge edge = asEdge(change.getData());
        if (edge == null || isBlank(edge.getFromNodeId()) || isBlank(edge.getToNodeId())) {
            if (skipValidation) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Edge invalida. Requiere from y to");
        }

        List<WorkflowEdge> edges = edgesOf(state);
        int idx = findEdgeIndex(edges, edge);
        if (idx >= 0) {
            edges.set(idx, edge);
        } else {
            edges.add(edge);
        }

        state.setEdges(edges);
        state.setLastModified(System.currentTimeMillis());

        // Mantener indice por edgeId para soportar edge_deleted con solo edgeId.
        String workflowId = change.getWorkflowId();
        if (workflowId != null && !workflowId.isBlank()) {
            Map<String, WorkflowEdge> workflowEdges = edgeIndex.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>());
            if (!isBlank(change.getEdgeId())) {
                workflowEdges.put(change.getEdgeId(), edge);
            }
            String canonicalId = buildCanonicalEdgeId(edge.getFromNodeId(), edge.getToNodeId());
            if (canonicalId != null) {
                workflowEdges.put(canonicalId, edge);
            }
        }
    }

    private void applyEdgeDelete(WorkflowEditingState state, WorkflowChangeMessage change, boolean skipValidation) {
        WorkflowEdge target = resolveEdgeTargetForDelete(state, change);
        if (target == null || isBlank(target.getFromNodeId()) || isBlank(target.getToNodeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Para edge_deleted envie data {fromNodeId,toNodeId} o un edgeId previamente indexado");
        }

        List<WorkflowEdge> edges = edgesOf(state);
        boolean removed = edges.removeIf(edge -> sameEdge(edge, target));
        if (!removed && !skipValidation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La edge no existe: " + target.getFromNodeId() + " -> " + target.getToNodeId());
        }

        state.setEdges(edges);
        state.setLastModified(System.currentTimeMillis());

        String workflowId = change.getWorkflowId();
        if (workflowId != null && !workflowId.isBlank()) {
            Map<String, WorkflowEdge> workflowEdges = edgeIndex.get(workflowId);
            if (workflowEdges != null) {
                if (!isBlank(change.getEdgeId())) {
                    workflowEdges.remove(change.getEdgeId());
                }
                String canonicalId = buildCanonicalEdgeId(target.getFromNodeId(), target.getToNodeId());
                if (canonicalId != null) {
                    workflowEdges.remove(canonicalId);
                }
                workflowEdges.entrySet().removeIf(entry -> sameEdge(entry.getValue(), target));
            }
        }
    }

    private void updateNodeLaneIndex(String workflowId, WorkflowChangeMessage change) {
        String nodeId = change.getNodeId();
        String laneId = extractLaneIdFromNodeData(change.getData());

        if (nodeId == null || nodeId.isBlank()) {
            return;
        }

        Map<String, String> laneByNode = nodeLaneIndex.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>());
        if (laneId == null || laneId.isBlank()) {
            laneByNode.remove(nodeId);
            return;
        }

        laneByNode.put(nodeId, laneId);
    }

    private void removeNodeFromIndex(String workflowId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        Map<String, String> laneByNode = nodeLaneIndex.get(workflowId);
        if (laneByNode != null) {
            laneByNode.remove(nodeId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowNode> nodesOf(WorkflowEditingState state) {
        if (state.getNodes() == null) {
            return new ArrayList<>();
        }
        return (List<WorkflowNode>) state.getNodes();
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowEdge> edgesOf(WorkflowEditingState state) {
        if (state.getEdges() == null) {
            return new ArrayList<>();
        }
        return (List<WorkflowEdge>) state.getEdges();
    }

    private int findNodeIndex(List<WorkflowNode> nodes, String nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            WorkflowNode node = nodes.get(i);
            if (node != null && nodeId.equals(node.getId())) {
                return i;
            }
        }
        return -1;
    }

    private int findEdgeIndex(List<WorkflowEdge> edges, WorkflowEdge target) {
        for (int i = 0; i < edges.size(); i++) {
            WorkflowEdge edge = edges.get(i);
            if (sameEdge(edge, target)) {
                return i;
            }
        }
        return -1;
    }

    private boolean sameEdge(WorkflowEdge left, WorkflowEdge right) {
        if (left == null || right == null) {
            return false;
        }
        return java.util.Objects.equals(left.getFromNodeId(), right.getFromNodeId())
            && java.util.Objects.equals(left.getToNodeId(), right.getToNodeId());
    }

    private WorkflowEdge resolveEdgeTargetForDelete(WorkflowEditingState state, WorkflowChangeMessage change) {
        WorkflowEdge fromData = asEdge(change.getData());
        if (fromData != null && !isBlank(fromData.getFromNodeId()) && !isBlank(fromData.getToNodeId())) {
            return fromData;
        }

        String workflowId = change.getWorkflowId();
        String edgeId = change.getEdgeId();
        if (workflowId != null && !workflowId.isBlank() && edgeId != null && !edgeId.isBlank()) {
            Map<String, WorkflowEdge> workflowEdges = edgeIndex.get(workflowId);
            if (workflowEdges != null) {
                WorkflowEdge indexed = workflowEdges.get(edgeId);
                if (indexed != null) {
                    return indexed;
                }
            }

            // Compatibilidad: si edgeId viene como "fromNodeId->toNodeId".
            String[] parts = edgeId.split("->", 2);
            if (parts.length == 2 && !isBlank(parts[0]) && !isBlank(parts[1])) {
                WorkflowEdge parsed = new WorkflowEdge();
                parsed.setFromNodeId(parts[0]);
                parsed.setToNodeId(parts[1]);
                return parsed;
            }
        }

        return null;
    }

    private String buildCanonicalEdgeId(String fromNodeId, String toNodeId) {
        if (isBlank(fromNodeId) || isBlank(toNodeId)) {
            return null;
        }
        return fromNodeId + "->" + toNodeId;
    }

    private WorkflowNode asNode(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof WorkflowNode node) {
            return node;
        }
        return objectMapper.convertValue(data, WorkflowNode.class);
    }

    private Lane asLane(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Lane lane) {
            return lane;
        }
        return objectMapper.convertValue(data, Lane.class);
    }

    private WorkflowEdge asEdge(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof WorkflowEdge edge) {
            return edge;
        }
        return objectMapper.convertValue(data, WorkflowEdge.class);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isDraftWorkflow(String workflowId) {
        return workflowRepository.findById(toObjectId(workflowId))
            .map(Workflow::getEstado)
            .map(status -> status == WorkflowStatus.borrador)
            .orElse(false);
    }

    private void persistEditingState(String workflowId, WorkflowEditingState state) {
        Workflow workflow = workflowRepository.findById(toObjectId(workflowId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Workflow no encontrado para persistir cambios: " + workflowId));

        workflow.setLanes(new ArrayList<>(lanesOf(state)));
        workflow.setNodes(new ArrayList<>(nodesOf(state)));
        workflow.setEdges(new ArrayList<>(edgesOf(state)));

        workflowRepository.save(workflow);
    }

    @SuppressWarnings("unchecked")
    private List<Lane> lanesOf(WorkflowEditingState state) {
        if (state.getLanes() == null) {
            return new ArrayList<>();
        }
        return (List<Lane>) state.getLanes();
    }

    private int findLaneIndex(List<Lane> lanes, String laneId) {
        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            if (lane != null && laneId.equals(lane.getId())) {
                return i;
            }
        }
        return -1;
    }

    private void normalizeLaneOrder(List<Lane> lanes) {
        lanes.sort(Comparator.comparingInt(l -> l.getOrden() == null ? Integer.MAX_VALUE : l.getOrden()));
        for (int i = 0; i < lanes.size(); i++) {
            lanes.get(i).setOrden(i);
        }
    }

    private String extractLaneId(WorkflowChangeMessage change) {
        if (change.getLaneId() != null && !change.getLaneId().isBlank()) {
            return change.getLaneId();
        }
        if (change.getData() instanceof Lane lane) {
            return lane.getId();
        }
        if (change.getData() instanceof Map<?, ?> map) {
            Object value = map.get("id");
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }

    private String extractLaneIdFromNodeData(Object data) {
        if (data instanceof WorkflowNode node) {
            return node.getLaneId();
        }
        if (data instanceof Map<?, ?> map) {
            Object value = map.get("laneId");
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }

    private ObjectId toObjectId(String value) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
