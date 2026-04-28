package com.workflow.demo.domain.embedded;

import java.time.Instant;

import lombok.Data;

@Data
public class SummaryData {
    private String titulo;
    private String descripcionCliente;
    private Instant ultimaActualizacionVisible;
}