package com.workflow.demo.config;

import java.io.IOException;

import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    @Bean
    public Module objectIdModule() {
        SimpleModule module = new SimpleModule();
        
        // Serializador: ObjectId -> String hex
        module.addSerializer(ObjectId.class, new JsonSerializer<ObjectId>() {
            @Override
            public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.toHexString());
                }
            }
        });
        
        // Deserializador: String hex -> ObjectId
        module.addDeserializer(ObjectId.class, new ObjectIdDeserializer());
        
        return module;
    }

    public static class ObjectIdDeserializer extends JsonDeserializer<ObjectId> {
        @Override
        public ObjectId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                return new ObjectId(value);
            } catch (IllegalArgumentException e) {
                // Si ya viene como el objeto anidado por algún error previo, intentar extraer el timestamp o manejarlo
                if (p.isExpectedStartObjectToken()) {
                    // Esto es para casos donde ya está corrupto en el JSON
                    return null; 
                }
                throw e;
            }
        }
    }
}
