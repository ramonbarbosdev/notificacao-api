package com.notificacao_api.exception;

import org.springframework.http.ResponseEntity;
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
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiErrorResponse(
                        ex.getStatusCode().value(),
                        ex.getReason() != null ? ex.getReason() : "Erro na requisição.",
                        ex.getStatusCode().toString(),
                        request.getRequestURI()
                ));
    }
}