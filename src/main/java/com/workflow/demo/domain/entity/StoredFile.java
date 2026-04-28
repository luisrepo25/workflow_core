package com.workflow.demo.domain.entity;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "files")
public class StoredFile {
    @Id
    private ObjectId id;

    private String nombreOriginal;
    private String storagePath;
    private String url;
    private String mimeType;
    private Long sizeBytes;

    @Indexed
    private ObjectId subidoPor;

    @Indexed
    private ObjectId processInstanceId;

    private String nodeId;

    @CreatedDate
    private Instant createdAt;
}