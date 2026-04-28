package com.workflow.demo.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.workflow.demo.domain.entity.User;

public interface UserRepository extends MongoRepository<User, ObjectId> {
    Optional<User> findByEmail(String email);

    List<User> findByRoleId(ObjectId roleId);

    List<User> findByDepartmentId(ObjectId departmentId);

    List<User> findByActivo(boolean activo);
}