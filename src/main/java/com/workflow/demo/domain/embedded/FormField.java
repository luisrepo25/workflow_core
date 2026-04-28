package com.workflow.demo.domain.embedded;

import java.util.ArrayList;
import java.util.List;

import com.workflow.demo.domain.enums.FieldType;

import lombok.Data;

@Data
public class FormField {
    private String id;
    private String label;
    private FieldType tipo;
    private boolean required = false;
    private List<String> options = new ArrayList<>();
    private String placeholder;
}