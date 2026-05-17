package com.notificacao_api.service;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.Plano;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.PlanoRepository;
import com.notificacao_api.repository.TemplateNotificacaoRepository;
import com.notificacao_api.repository.UsuarioOrganizacaoRepository;

@Service
public class PlanoLimiteService {

    private final OrganizacaoRepository organizacaoRepository;
    private final PlanoRepository planoRepository;
    private final TemplateNotificacaoRepository templateRepository;
    private final UsuarioOrganizacaoRepository usuarioOrganizacaoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final FeatureFlagService featureFlagService;

    public PlanoLimiteService(
            OrganizacaoRepository organizacaoRepository,
            PlanoRepository planoRepository,
            TemplateNotificacaoRepository templateRepository,
            UsuarioOrganizacaoRepository usuarioOrganizacaoRepository,
            NotificacaoRepository notificacaoRepository,
            FeatureFlagService featureFlagService) {
        this.organizacaoRepository = organizacaoRepository;
        this.planoRepository = planoRepository;
        this.templateRepository = templateRepository;
        this.usuarioOrganizacaoRepository = usuarioOrganizacaoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.featureFlagService = featureFlagService;
    }

    public void validarCriacaoTemplate(Long idOrganizacao) {
        featureFlagService.validarRecursoHabilitado(idOrganizacao, RecursoFeature.TEMPLATES);
        Plano plano = planoDaOrganizacao(idOrganizacao);
        if (plano.getNuLimiteTemplates() != null
                && templateRepository.countByIdOrganizacao(idOrganizacao) >= plano.getNuLimiteTemplates()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de templates do plano excedido.");
        }
    }

    public void validarCriacaoUsuario(Long idOrganizacao) {
        Plano plano = planoDaOrganizacao(idOrganizacao);
        if (plano.getNuLimiteUsuarios() != null
                && usuarioOrganizacaoRepository.countByOrganizacaoIdOrganizacaoAndFlAtivoTrue(idOrganizacao) >= plano.getNuLimiteUsuarios()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de usuarios do plano excedido.");
        }
    }

    public void validarEnvioNotificacao(Long idOrganizacao, CanalNotificacao canal) {
        validarCanal(idOrganizacao, canal);
        Plano plano = planoDaOrganizacao(idOrganizacao);
        if (plano.getNuLimiteMensagensMensal() != null) {
            LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
            long enviadas = notificacaoRepository.countByIdOrganizacaoAndDtCriacaoAfter(idOrganizacao, inicioMes.atStartOfDay());
            if (enviadas >= plano.getNuLimiteMensagensMensal()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite mensal de mensagens do plano excedido.");
            }
        }
    }

    private void validarCanal(Long idOrganizacao, CanalNotificacao canal) {
        RecursoFeature recurso = switch (canal) {
            case WHATSAPP -> RecursoFeature.WHATSAPP;
            case EMAIL -> RecursoFeature.EMAIL;
            case TELEGRAM -> RecursoFeature.TELEGRAM;
            case WEBHOOK -> RecursoFeature.WEBHOOK;
        };
        featureFlagService.validarRecursoHabilitado(idOrganizacao, recurso);
    }

    private Plano planoDaOrganizacao(Long idOrganizacao) {
        Organizacao organizacao = organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada."));
        if (organizacao.getIdPlano() == null) {
            return planoRepository.findAll().stream()
                    .filter(Plano::getFlAtivo)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Organizacao sem plano ativo vinculado."));
        }
        return planoRepository.findById(organizacao.getIdPlano())
                .filter(Plano::getFlAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Plano da organizacao esta inativo ou nao existe."));
    }
}
