package com.workflow.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exc) {
        log.warn("⚠️ Intento de subida de archivo que excede el límite permitido: {}", exc.getMessage());
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Archivo demasiado grande");
        body.put("message", "El archivo excede el límite de tamaño permitido (10MB).");
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception exc) {
        log.error("❌ Error no controlado: ", exc);
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error interno del servidor");
        body.put("message", exc.getMessage());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
