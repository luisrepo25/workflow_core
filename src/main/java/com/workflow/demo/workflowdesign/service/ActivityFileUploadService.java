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

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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

    private final Cloudinary cloudinary;
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
                instance.getId(),
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
        ObjectId processInstanceId,
        String nodeId,
        String actividadId
    ) {
        try {
            String folder = String.format(
                "workflow/process_%s/activity_%s",
                processInstanceId.toHexString(),
                actividadId
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto",
                    "use_filename", true,
                    "unique_filename", true,
                    "overwrite", false
                )
            );

            String secureUrl = asString(uploadResult.get("secure_url"));
            String publicId = asString(uploadResult.get("public_id"));
            Instant now = Instant.now();

            StoredFile storedFile = new StoredFile();
            storedFile.setNombreOriginal(file.getOriginalFilename());
            storedFile.setStoragePath(publicId);
            storedFile.setUrl(secureUrl);
            storedFile.setMimeType(file.getContentType());
            storedFile.setSizeBytes(file.getSize());
            storedFile.setSubidoPor(actorId);
            storedFile.setProcessInstanceId(processInstanceId);
            storedFile.setNodeId(nodeId);
            storedFile.setCreatedAt(now);

            StoredFile saved = storedFileRepository.save(storedFile);

            Map<String, Object> fileValue = new HashMap<>();
            fileValue.put("fileId", saved.getId().toHexString());
            fileValue.put("url", secureUrl);
            fileValue.put("publicId", publicId);
            fileValue.put("nombre", file.getOriginalFilename());
            fileValue.put("mimeType", file.getContentType());
            fileValue.put("sizeBytes", file.getSize());
            fileValue.put("provider", "cloudinary");
            fileValue.put("uploadedAt", now.toString());
            return fileValue;
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo subir el archivo a Cloudinary", ex);
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
