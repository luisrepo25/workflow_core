package com.workflow.demo.repository;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.workflow.demo.domain.entity.StoredFile;

public interface StoredFileRepository extends MongoRepository<StoredFile, ObjectId> {
    List<StoredFile> findBySubidoPor(ObjectId subidoPor);

    List<StoredFile> findByProcessInstanceId(ObjectId processInstanceId);
}