package com.workflow.demo.workflowdesign.dto;

import java.util.ArrayList;
import java.util.List;

import com.workflow.demo.domain.embedded.Lane;
import com.workflow.demo.domain.embedded.WorkflowEdge;
import com.workflow.demo.domain.embedded.WorkflowNode;

import lombok.Data;

@Data
public class SaveWorkflowDesignRequest {
    private List<Lane> lanes = new ArrayList<>();
    private List<WorkflowNode> nodes = new ArrayList<>();
    private List<WorkflowEdge> edges = new ArrayList<>();
}