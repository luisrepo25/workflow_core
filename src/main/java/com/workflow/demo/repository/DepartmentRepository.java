package com.workflow.demo.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.workflow.demo.domain.entity.Department;

public interface DepartmentRepository extends MongoRepository<Department, ObjectId> {
}