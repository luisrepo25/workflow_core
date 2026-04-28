package com.workflow.demo.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.workflow.demo.domain.embedded.PushToken;

import lombok.Data;

@Data
@Document(collection = "users")
public class User {
    @Id
    private ObjectId id;

    private String nombre;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    @Indexed
    private ObjectId roleId;

    @Indexed
    private ObjectId departmentId;

    private boolean activo = true;
    private String telefono;
    @Field("fcm_token")
    private String fcmToken;
    private List<PushToken> pushTokens = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}