package com.workflow.demo.domain.embedded;

import java.time.Instant;

import com.workflow.demo.domain.enums.PushPlatform;

import lombok.Data;

@Data
public class PushToken {
    private String token;
    private PushPlatform platform;
    private Instant lastUsedAt;
    private boolean activo = true;
}