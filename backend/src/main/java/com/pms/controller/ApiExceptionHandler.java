package com.pms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = ProductionReportController.class)
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> serviceError(IllegalStateException e) {
        return ResponseEntity.status(502).body(body(502, e.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> error(int code, String message) {
        return ResponseEntity.badRequest().body(body(code, message));
    }

    private Map<String, Object> body(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("data", null);
        body.put("message", message);
        return body;
    }
}
