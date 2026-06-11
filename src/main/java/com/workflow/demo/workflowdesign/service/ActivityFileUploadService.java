package com.workflow.demo.workflowdesign.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.workflow.demo.workflowdesign.config.S3Properties;
import com.workflow.demo.domain.embedded.FormField;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.embedded.WorkflowSnapshot;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.entity.StoredFile;
import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.FieldType;
import com.workflow.demo.repository.StoredFileRepository;
import com.workflow.demo.repository.WorkflowRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityFileUploadService {

    private final S3StorageService s3StorageService;
    private final S3Properties s3Properties;
    private final StoredFileRepository storedFileRepository;
    private final WorkflowRepository workflowRepository;

    public Map<String, Object> injectUploadedFilesIntoRespuesta(
        ProcessInstance instance,
        String actividadId,
        String nodeId,
        ObjectId actorId,
        Map<String, Object> respuestaFormulario,
        List<MultipartFile> files,
        List<String> fileFieldIds
    ) {
        Map<String, Object> merged = respuestaFormulario == null
            ? new HashMap<>()
            : new HashMap<>(respuestaFormulario);

        if (files == null || files.isEmpty()) {
            return merged;
        }

        if (fileFieldIds == null || fileFieldIds.isEmpty()) {
            throw new IllegalArgumentException("fileFieldIds es requerido cuando se envian archivos");
        }

        if (files.size() != fileFieldIds.size()) {
            throw new IllegalArgumentException("files y fileFieldIds deben tener el mismo tamaño");
        }

        WorkflowNode currentNode = resolveCurrentNode(instance, nodeId);
        Set<String> fileFieldSet = resolveFileFieldIds(currentNode);

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String fieldId = fileFieldIds.get(i);

            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Se recibio un archivo vacio en posicion " + i);
            }
            if (fieldId == null || fieldId.isBlank()) {
                throw new IllegalArgumentException("fileFieldIds contiene un valor vacio en posicion " + i);
            }
            if (!fileFieldSet.contains(fieldId)) {
                throw new IllegalArgumentException(
                    "El fieldId '" + fieldId + "' no corresponde a un campo tipo=file del nodo " + nodeId);
            }

            Map<String, Object> fileValue = uploadAndPersist(
                file,
                actorId,
                instance,
                nodeId,
                actividadId
            );

            mergeFieldValue(merged, fieldId, fileValue);
        }

        return merged;
    }

    private WorkflowNode resolveCurrentNode(ProcessInstance instance, String nodeId) {
        WorkflowSnapshot snapshot = instance.getWorkflowSnapshot();
        if (snapshot != null) {
            WorkflowNode snapshotNode = snapshot.getNodeById(nodeId);
            if (snapshotNode != null) {
                return snapshotNode;
            }
        }

        Workflow workflow = workflowRepository.findById(instance.getWorkflowId())
            .orElseThrow(() -> new IllegalArgumentException("No se encontro workflow para la instancia"));

        return workflow.getNodes().stream()
            .filter(n -> nodeId.equals(n.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No se encontro el nodo " + nodeId));
    }

    private Set<String> resolveFileFieldIds(WorkflowNode node) {
        if (node.getForm() == null || node.getForm().getCampos() == null) {
            return Set.of();
        }

        Set<String> ids = new HashSet<>();
        for (FormField field : node.getForm().getCampos()) {
            if (field.getTipo() == FieldType.file) {
                ids.add(field.getId());
            }
        }
        return ids;
    }

    private Map<String, Object> uploadAndPersist(
        MultipartFile file,
        ObjectId actorId,
        ProcessInstance instance,
        String nodeId,
        String actividadId
    ) {
        try {
            ObjectId processInstanceId = instance.getId();
            ObjectId clienteId = instance.getClienteId();
            
            long timestamp = Instant.now().toEpochMilli();
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "file_" + timestamp;
            }
            
            String s3Key = String.format("clientes/%s/tramites/%s/%s/%d_%s",
                    clienteId != null ? clienteId.toHexString() : "unknown",
                    processInstanceId.toHexString(),
                    nodeId,
                    timestamp,
                    originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_")
            );

            String secureUrl = s3StorageService.uploadFile(file, s3Key);
            Instant now = Instant.now();

            StoredFile storedFile = new StoredFile();
            storedFile.setNombreOriginal(originalFilename);
            storedFile.setStoragePath(s3Key);
            storedFile.setUrl(secureUrl);
            storedFile.setMimeType(file.getContentType());
            storedFile.setSizeBytes(file.getSize());
            storedFile.setSubidoPor(actorId);
            storedFile.setProcessInstanceId(processInstanceId);
            storedFile.setNodeId(nodeId);
            storedFile.setCreatedAt(now);
            
            // Campos S3
            storedFile.setS3Key(s3Key);
            storedFile.setS3Bucket(s3Properties.getS3().getBucket());
            storedFile.setClienteId(clienteId);

            StoredFile saved = storedFileRepository.save(storedFile);

            Map<String, Object> fileValue = new HashMap<>();
            fileValue.put("fileId", saved.getId().toHexString());
            fileValue.put("url", secureUrl);
            fileValue.put("publicId", s3Key);
            fileValue.put("nombre", originalFilename);
            fileValue.put("mimeType", file.getContentType());
            fileValue.put("sizeBytes", file.getSize());
            fileValue.put("provider", "s3");
            fileValue.put("uploadedAt", now.toString());
            return fileValue;
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo subir el archivo a S3", ex);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private void mergeFieldValue(Map<String, Object> respuestaFormulario, String fieldId, Map<String, Object> fileValue) {
        Object existing = respuestaFormulario.get(fieldId);
        if (existing == null) {
            respuestaFormulario.put(fieldId, fileValue);
            return;
        }

        if (existing instanceof List<?> existingList) {
            List<Object> mutable = new ArrayList<>(existingList);
            mutable.add(fileValue);
            respuestaFormulario.put(fieldId, mutable);
            return;
        }

        List<Object> multi = new ArrayList<>();
        multi.add(existing);
        multi.add(fileValue);
        respuestaFormulario.put(fieldId, multi);
    }
}
