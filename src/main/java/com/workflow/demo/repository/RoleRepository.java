package com.workflow.demo.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.workflow.demo.domain.entity.Role;

public interface RoleRepository extends MongoRepository<Role, ObjectId> {
    Optional<Role> findByNombre(String nombre);
}