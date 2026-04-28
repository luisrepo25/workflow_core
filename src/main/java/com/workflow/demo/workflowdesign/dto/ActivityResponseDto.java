package com.workflow.demo.workflowdesign.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.workflow.demo.domain.embedded.FormField;
import com.workflow.demo.domain.embedded.NodeForm;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.domain.embedded.WorkflowNode;
import com.workflow.demo.domain.enums.ActivityStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuestas de actividades dentro de un ProcessInstance.
 * Incluye el formulario que debe rellenar el funcionario (tomado del WorkflowNode via snapshot).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityResponseDto {

    /** UUID de la actividad dentro del proceso */
    @JsonProperty("id")
    private String id;

    /** ID del nodo del workflow al que corresponde esta actividad */
    @JsonProperty("nodeId")
    private String nodeId;

    /** Nombre descriptivo del nodo (ej: "Revisión de documentos") */
    @JsonProperty("nombre")
    private String nombre;

    @JsonProperty("estado")
    private ActivityStatus estado;

    @JsonProperty("responsableTipo")
    private String responsableTipo;

    /** Usuario asignado directamente (solo cuando responsableTipo = usuario) */
    @JsonProperty("usuarioId")
    private String usuarioId;

    /** Departamento asignado (solo cuando responsableTipo = departamento) */
    @JsonProperty("departmentId")
    private String departmentId;

    /**
     * Formulario que el funcionario debe rellenar.
     * Proviene de WorkflowNode.form (guardado en el WorkflowSnapshot del trámite).
     * Null si el nodo no tiene formulario configurado.
     */
    @JsonProperty("formulario")
    private NodeFormDto formulario;

    /** Respuesta ya guardada (vacío si aún no se completó) */
    @JsonProperty("respuestaFormulario")
    private Map<String, Object> respuestaFormulario;

    /** SLA en minutos configurado para este nodo */
    @JsonProperty("slaMinutos")
    private Integer slaMinutos;

    /** Indica si el nodo permite adjuntar archivos */
    @JsonProperty("permiteAdjuntos")
    private boolean permiteAdjuntos;

    @JsonProperty("fechaInicio")
    private Instant fechaInicio;

    @JsonProperty("fechaFin")
    private Instant fechaFin;

    // -------------------------------------------------------------------------
    // DTO anidado para el formulario
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeFormDto {
        @JsonProperty("titulo")
        private String titulo;

        @JsonProperty("descripcion")
        private String descripcion;

        @JsonProperty("campos")
        private List<FormFieldDto> campos;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FormFieldDto {
        @JsonProperty("id")
        private String id;

        @JsonProperty("label")
        private String label;

        /** Tipo de dato: text | textarea | number | date | bool | select | file */
        @JsonProperty("tipo")
        private String tipo;

        @JsonProperty("required")
        private boolean required;

        @JsonProperty("options")
        private List<String> options;

        @JsonProperty("placeholder")
        private String placeholder;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Mapeo básico sin información del nodo (el formulario quedará null).
     * Usar solo cuando no se disponga del WorkflowSnapshot.
     */
    public static ActivityResponseDto from(ProcessActivity activity) {
        return from(activity, null);
    }

    /**
     * Mapeo enriquecido: incluye nombre, formulario, slaMinutos y permiteAdjuntos
     * tomados del WorkflowNode correspondiente (obtenido del snapshot del trámite).
     *
     * @param activity actividad del proceso
     * @param node     nodo del workflow (puede ser null si no está disponible)
     */
    public static ActivityResponseDto from(ProcessActivity activity, WorkflowNode node) {
        if (activity == null) {
            return null;
        }

        NodeFormDto formularioDto = null;
        Integer slaMinutos = null;
        boolean permiteAdjuntos = false;
        String nombreNodo = activity.getNombre(); // nombre guardado en la actividad al crearla

        if (node != null) {
            slaMinutos = node.getSlaMinutos();
            permiteAdjuntos = node.isPermiteAdjuntos();
            // Sobrescribir con el nombre canónico del nodo si está disponible
            if (node.getNombre() != null) {
                nombreNodo = node.getNombre();
            }
            formularioDto = mapNodeForm(node.getForm());
        }

        return ActivityResponseDto.builder()
                .id(activity.getActividadId())
                .nodeId(activity.getNodeId())
                .nombre(nombreNodo)
                .estado(activity.getEstado())
                .responsableTipo(activity.getResponsableTipo() != null
                        ? activity.getResponsableTipo().toString() : null)
                .usuarioId(activity.getUsuarioId() != null
                        ? activity.getUsuarioId().toHexString() : null)
                .departmentId(activity.getDepartmentId() != null
                        ? activity.getDepartmentId().toHexString() : null)
                .formulario(formularioDto)
                .respuestaFormulario(activity.getRespuestaFormulario())
                .slaMinutos(slaMinutos)
                .permiteAdjuntos(permiteAdjuntos)
                .fechaInicio(activity.getFechaInicio())
                .fechaFin(activity.getFechaFin())
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static NodeFormDto mapNodeForm(NodeForm form) {
        if (form == null) {
            return null;
        }
        List<FormFieldDto> campos = form.getCampos() == null ? List.of()
                : form.getCampos().stream()
                        .map(ActivityResponseDto::mapFormField)
                        .toList();
        return NodeFormDto.builder()
                .titulo(form.getTitulo())
                .descripcion(form.getDescripcion())
                .campos(campos)
                .build();
    }

    private static FormFieldDto mapFormField(FormField f) {
        if (f == null) return null;
        return FormFieldDto.builder()
                .id(f.getId())
                .label(f.getLabel())
                .tipo(f.getTipo() != null ? f.getTipo().name() : null)
                .required(f.isRequired())
                .options(f.getOptions())
                .placeholder(f.getPlaceholder())
                .build();
    }
}
