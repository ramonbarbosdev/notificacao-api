package com.notificacao_api.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiErrorResponse(
                        ex.getStatusCode().value(),
                        ex.getReason() != null ? ex.getReason() : "Erro na requisicao.",
                        ex.getStatusCode().toString(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .orElse("Dados invalidos.");

        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(400, mensagem, "BAD_REQUEST", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(403)
                .body(new ApiErrorResponse(403, "Acesso negado.", "FORBIDDEN", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(500)
                .body(new ApiErrorResponse(
                        500,
                        "Erro interno do servidor.",
                        "INTERNAL_SERVER_ERROR",
                        request.getRequestURI()));
    }
}
