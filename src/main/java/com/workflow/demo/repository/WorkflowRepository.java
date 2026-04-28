package com.workflow.demo.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.workflow.demo.domain.entity.Workflow;
import com.workflow.demo.domain.enums.WorkflowStatus;

public interface WorkflowRepository extends MongoRepository<Workflow, ObjectId> {
    List<Workflow> findByEstado(WorkflowStatus estado);

    List<Workflow> findByCreatedBy(ObjectId createdBy);

    Optional<Workflow> findByCodigo(String codigo);
}