package com.notificacao_api.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> statusSuperAdmin() {
        return ResponseEntity.ok(Map.of("message", "Acesso permitido para SUPER_ADMIN"));
    }
}
