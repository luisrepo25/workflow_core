package com.workflow.demo.domain.embedded;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class NodeForm {
    private String titulo;
    private String descripcion;
    private List<FormField> campos = new ArrayList<>();
}