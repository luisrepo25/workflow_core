package com.workflow.demo.workflowdesign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "aws")
public class S3Properties {
    private S3 s3 = new S3();
    private String accessKey;
    private String secretKey;
    private String region;

    @Data
    public static class S3 {
        private String bucket;
    }
}
