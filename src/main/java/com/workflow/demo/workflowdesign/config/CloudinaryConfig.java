package com.workflow.demo.workflowdesign.config;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        return new Cloudinary(Map.of(
            "cloud_name", properties.getCloudName(),
            "api_key", properties.getApiKey(),
            "api_secret", properties.getApiSecret(),
            "secure", true
        ));
    }
}
