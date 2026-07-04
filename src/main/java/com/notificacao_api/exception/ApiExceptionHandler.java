package com.notificacao_api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.ApiErrorResponse;
import com.notificacao_api.exception.WhatsappNaoConectadoException;
import com.notificacao_api.service.whatsapp.WhatsappGatewayErroUtil;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(WhatsappNaoConectadoException.class)
    public ResponseEntity<ApiErrorResponse> handleWhatsappNaoConectado(
            WhatsappNaoConectadoException ex,
            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        WhatsappNaoConectadoException.MENSAGEM,
                        WhatsappNaoConectadoException.CODIGO,
                        request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        String mensagem = ex.getReason() != null ? ex.getReason() : "Erro na requisicao.";
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiErrorResponse(
                        ex.getStatusCode().value(),
                        mensagem,
                        ex.getStatusCode().toString(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceAccess(
            ResourceAccessException ex,
            HttpServletRequest request) {
        log.warn("Falha de rede em {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(
                        503,
                        WhatsappGatewayErroUtil.mensagemParaUsuario(ex),
                        "GATEWAY_INDISPONIVEL",
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
        log.error("Erro nao tratado em {}", request.getRequestURI(), ex);
        return ResponseEntity
                .status(500)
                .body(new ApiErrorResponse(
                        500,
                        "Erro interno do servidor.",
                        "INTERNAL_SERVER_ERROR",
                        request.getRequestURI()));
    }
}
