package com.workflow.demo.dashboard.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardMetricsResponse {
    private long totalTramites;
    private long tramitesActivos;
    private long tramitesCompletados;
    private double tiempoMedioResolucionMinutos;
    private long actividadesPendientesTotales;
    private double tasaRechazoPorcentual;
    private Map<String, Long> tramitesPorWorkflowNombre;
    private List<BottleneckDto> cuellosDeBotella;
    private Map<String, Long> tramitesPorEstado;
    private Map<String, Long> tramitesPorDepartamento;

    @Data
    @Builder
    public static class BottleneckDto {
        private String nodeId;
        private String nodeName;
        private long averageTimeMinutos;
        private long pendingCount;
    }
}
