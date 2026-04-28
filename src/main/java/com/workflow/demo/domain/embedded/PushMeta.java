package com.workflow.demo.domain.embedded;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class PushMeta {
    private List<String> tokens = new ArrayList<>();
    private boolean enviado = false;
    private Instant fechaEnvio;
    private String error;
}