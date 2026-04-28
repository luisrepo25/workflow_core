package com.workflow.demo.dashboard.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.workflow.demo.dashboard.dto.DashboardMetricsResponse;
import com.workflow.demo.dashboard.dto.DashboardMetricsResponse.BottleneckDto;
import com.workflow.demo.domain.embedded.ProcessActivity;
import com.workflow.demo.domain.entity.ProcessInstance;
import com.workflow.demo.domain.enums.ProcessStatus;
import com.workflow.demo.repository.ProcessInstanceRepository;
import com.workflow.demo.repository.WorkflowRepository;
import com.workflow.demo.domain.entity.Workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowRepository workflowRepository;

    public DashboardMetricsResponse getMetrics() {
        List<ProcessInstance> instances = processInstanceRepository.findAll();

        long total = instances.size();
        long activos = instances.stream()
            .filter(i -> i.getEstado() == ProcessStatus.pendiente || i.getEstado() == ProcessStatus.en_proceso || i.getEstado() == ProcessStatus.correccion)
            .count();
        long completados = instances.stream()
            .filter(i -> i.getEstado() == ProcessStatus.finalizado || i.getEstado() == ProcessStatus.aprobado)
            .count();

        double tiempoMedio = instances.stream()
            .filter(i -> i.getFinishedAt() != null && i.getCreatedAt() != null)
            .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getFinishedAt()).toMinutes())
            .average()
            .orElse(0.0);

        Map<String, Long> porEstado = instances.stream()
            .collect(Collectors.groupingBy(i -> i.getEstado().name(), Collectors.counting()));

        List<Workflow> workflows = workflowRepository.findAll();
        Map<String, String> wfNames = workflows.stream()
            .collect(Collectors.toMap(w -> w.getId().toHexString(), Workflow::getNombre));

        long actividadesPendientesTotales = 0;
        long totalFinalizados = 0;
        long totalRechazados = 0;

        Map<String, Long> pendingByNode = new HashMap<>();
        Map<String, String> nodeNames = new HashMap<>();
        Map<String, List<Long>> timeByNode = new HashMap<>();
        Map<String, Long> countByDept = new HashMap<>();
        Map<String, Long> countByWf = new HashMap<>();

        for (ProcessInstance pi : instances) {
            String wfName = wfNames.getOrDefault(pi.getWorkflowId().toHexString(), "Desconocido");
            countByWf.put(wfName, countByWf.getOrDefault(wfName, 0L) + 1);

            if (pi.getEstado() == ProcessStatus.finalizado || pi.getEstado() == ProcessStatus.aprobado || pi.getEstado() == ProcessStatus.rechazado || pi.getEstado() == ProcessStatus.cancelado) {
                totalFinalizados++;
                if (pi.getEstado() == ProcessStatus.rechazado) {
                    totalRechazados++;
                }
            }

            if (pi.getActividades() != null) {
                for (ProcessActivity act : pi.getActividades()) {
                    if (act.getEstado() != null && (act.getEstado().name().equals("pendiente") || act.getEstado().name().equals("en_ejecucion"))) {
                        actividadesPendientesTotales++;
                    }

                    nodeNames.put(act.getNodeId(), act.getNombre());
                    
                    if (act.getDepartmentId() != null) {
                        String deptIdStr = act.getDepartmentId().toHexString();
                        countByDept.put(deptIdStr, countByDept.getOrDefault(deptIdStr, 0L) + 1);
                    }

                    if (act.getEstado() != null && act.getEstado().name().equals("pendiente")) {
                        pendingByNode.put(act.getNodeId(), pendingByNode.getOrDefault(act.getNodeId(), 0L) + 1);
                    }

                    if (act.getFechaInicio() != null && act.getFechaFin() != null) {
                        long mins = Duration.between(act.getFechaInicio(), act.getFechaFin()).toMinutes();
                        timeByNode.computeIfAbsent(act.getNodeId(), k -> new ArrayList<>()).add(mins);
                    }
                }
            }
        }

        List<BottleneckDto> bottlenecks = new ArrayList<>();
        for (String nodeId : nodeNames.keySet()) {
            long pending = pendingByNode.getOrDefault(nodeId, 0L);
            List<Long> times = timeByNode.getOrDefault(nodeId, new ArrayList<>());
            double avgTime = times.stream().mapToLong(l -> l).average().orElse(0.0);
            
            bottlenecks.add(BottleneckDto.builder()
                .nodeId(nodeId)
                .nodeName(nodeNames.get(nodeId))
                .averageTimeMinutos((long) avgTime)
                .pendingCount(pending)
                .build());
        }

        bottlenecks.sort((b1, b2) -> Long.compare(b2.getPendingCount(), b1.getPendingCount()));
        if (bottlenecks.size() > 5) {
            bottlenecks = bottlenecks.subList(0, 5);
        }

        double tasaRechazo = totalFinalizados > 0 ? ((double) totalRechazados / totalFinalizados) * 100.0 : 0.0;

        return DashboardMetricsResponse.builder()
            .totalTramites(total)
            .tramitesActivos(activos)
            .tramitesCompletados(completados)
            .tiempoMedioResolucionMinutos(tiempoMedio)
            .actividadesPendientesTotales(actividadesPendientesTotales)
            .tasaRechazoPorcentual(tasaRechazo)
            .tramitesPorWorkflowNombre(countByWf)
            .cuellosDeBotella(bottlenecks)
            .tramitesPorEstado(porEstado)
            .tramitesPorDepartamento(countByDept)
            .build();
    }
}
