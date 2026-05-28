package com.notificacao_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.configuracao.AuditoriaEventoResponse;
import com.notificacao_api.model.AuditoriaEvento;
import com.notificacao_api.repository.AuditoriaEventoRepository;
import com.notificacao_api.security.JwtAuthentication;

@Service
public class AuditoriaEventoService {

    private final AuditoriaEventoRepository auditoriaRepository;
    private final TenantContextService tenantContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditoriaEventoService(
            AuditoriaEventoRepository auditoriaRepository,
            TenantContextService tenantContextService) {
        this.auditoriaRepository = auditoriaRepository;
        this.tenantContextService = tenantContextService;
    }

    @Transactional
    public void registrar(
            Long idOrganizacao,
            String modulo,
            String acao,
            String descricao,
            Object dadosAntes,
            Object dadosDepois) {

        JwtAuthentication atual = tenantContextService.atual();
        registrarComContexto(
                idOrganizacao,
                atual.getIdUsuario(),
                atual.getRole() != null ? atual.getRole() : atual.getTipoGlobal(),
                modulo,
                acao,
                descricao,
                dadosAntes,
                dadosDepois);
    }

    @Transactional
    public void registrarSistema(
            Long idOrganizacao,
            String modulo,
            String acao,
            String descricao,
            Object dadosAntes,
            Object dadosDepois) {
        registrarComContexto(
                idOrganizacao,
                null,
                "SISTEMA",
                modulo,
                acao,
                descricao,
                dadosAntes,
                dadosDepois);
    }

    private void registrarComContexto(
            Long idOrganizacao,
            Long idUsuario,
            String role,
            String modulo,
            String acao,
            String descricao,
            Object dadosAntes,
            Object dadosDepois) {

        AuditoriaEvento evento = new AuditoriaEvento();
        evento.setIdOrganizacao(idOrganizacao);
        evento.setIdUsuario(idUsuario);
        evento.setRole(role);
        evento.setModulo(modulo);
        evento.setAcao(acao);
        evento.setDescricao(descricao);
        evento.setDadosAntes(toJson(dadosAntes));
        evento.setDadosDepois(toJson(dadosDepois));

        auditoriaRepository.save(evento);
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaEventoResponse> listarGlobal(Pageable pageable) {
        return auditoriaRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaEventoResponse> listarOrganizacao(Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return auditoriaRepository.findByIdOrganizacao(idOrganizacao, pageable).map(this::toResponse);
    }

    private String toJson(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            return String.valueOf(valor);
        }
    }

    private AuditoriaEventoResponse toResponse(AuditoriaEvento evento) {
        return new AuditoriaEventoResponse(
                evento.getIdAuditoria(),
                evento.getIdOrganizacao(),
                evento.getIdUsuario(),
                evento.getRole(),
                evento.getModulo(),
                evento.getAcao(),
                evento.getDescricao(),
                evento.getIp(),
                evento.getUserAgent(),
                evento.getDadosAntes(),
                evento.getDadosDepois(),
                evento.getDtCriacao());
    }
}
