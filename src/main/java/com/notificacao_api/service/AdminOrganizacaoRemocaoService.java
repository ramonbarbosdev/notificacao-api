package com.notificacao_api.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.common.TipoGlobal;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.Usuario;
import com.notificacao_api.model.UsuarioOrganizacao;
import com.notificacao_api.repository.AlertaOperacionalRepository;
import com.notificacao_api.repository.AuditoriaEventoRepository;
import com.notificacao_api.repository.AuditoriaNotificacaoRepository;
import com.notificacao_api.repository.ConfiguracaoProvedorNotificacaoRepository;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoApiKeyRepository;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.repository.OrganizacaoFeatureFlagRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.OrganizacaoWebhookRepository;
import com.notificacao_api.repository.TemplateNotificacaoRepository;
import com.notificacao_api.repository.UsuarioOrganizacaoRepository;
import com.notificacao_api.repository.UsuarioRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;

@Service
public class AdminOrganizacaoRemocaoService {

    private static final long ID_ORGANIZACAO_PROTEGIDA = 1L;

    private final OrganizacaoRepository organizacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioOrganizacaoRepository usuarioOrganizacaoRepository;
    private final AuditoriaNotificacaoRepository auditoriaNotificacaoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final TemplateNotificacaoRepository templateNotificacaoRepository;
    private final ConfiguracaoProvedorNotificacaoRepository configuracaoProvedorNotificacaoRepository;
    private final WhatsappSessionRepository whatsappSessionRepository;
    private final OrganizacaoWebhookRepository organizacaoWebhookRepository;
    private final OrganizacaoApiKeyRepository organizacaoApiKeyRepository;
    private final OrganizacaoFeatureFlagRepository organizacaoFeatureFlagRepository;
    private final OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository;
    private final AlertaOperacionalRepository alertaOperacionalRepository;
    private final AuditoriaEventoRepository auditoriaEventoRepository;

    public AdminOrganizacaoRemocaoService(
            OrganizacaoRepository organizacaoRepository,
            UsuarioRepository usuarioRepository,
            UsuarioOrganizacaoRepository usuarioOrganizacaoRepository,
            AuditoriaNotificacaoRepository auditoriaNotificacaoRepository,
            NotificacaoRepository notificacaoRepository,
            TemplateNotificacaoRepository templateNotificacaoRepository,
            ConfiguracaoProvedorNotificacaoRepository configuracaoProvedorNotificacaoRepository,
            WhatsappSessionRepository whatsappSessionRepository,
            OrganizacaoWebhookRepository organizacaoWebhookRepository,
            OrganizacaoApiKeyRepository organizacaoApiKeyRepository,
            OrganizacaoFeatureFlagRepository organizacaoFeatureFlagRepository,
            OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository,
            AlertaOperacionalRepository alertaOperacionalRepository,
            AuditoriaEventoRepository auditoriaEventoRepository) {
        this.organizacaoRepository = organizacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioOrganizacaoRepository = usuarioOrganizacaoRepository;
        this.auditoriaNotificacaoRepository = auditoriaNotificacaoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.templateNotificacaoRepository = templateNotificacaoRepository;
        this.configuracaoProvedorNotificacaoRepository = configuracaoProvedorNotificacaoRepository;
        this.whatsappSessionRepository = whatsappSessionRepository;
        this.organizacaoWebhookRepository = organizacaoWebhookRepository;
        this.organizacaoApiKeyRepository = organizacaoApiKeyRepository;
        this.organizacaoFeatureFlagRepository = organizacaoFeatureFlagRepository;
        this.organizacaoConfiguracaoRepository = organizacaoConfiguracaoRepository;
        this.alertaOperacionalRepository = alertaOperacionalRepository;
        this.auditoriaEventoRepository = auditoriaEventoRepository;
    }

    @Transactional
    public void removerOrganizacaoPermanentemente(Long idOrganizacao) {
        if (ID_ORGANIZACAO_PROTEGIDA == idOrganizacao) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A organizacao demo nao pode ser removida permanentemente");
        }

        Organizacao organizacao = organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organizacao nao encontrada"));

        auditoriaNotificacaoRepository.deleteByIdOrganizacao(idOrganizacao);
        notificacaoRepository.deleteByIdOrganizacao(idOrganizacao);
        templateNotificacaoRepository.deleteByIdOrganizacao(idOrganizacao);
        configuracaoProvedorNotificacaoRepository.deleteByIdOrganizacao(idOrganizacao);
        whatsappSessionRepository.deleteByIdOrganizacao(idOrganizacao);
        organizacaoWebhookRepository.deleteByIdOrganizacao(idOrganizacao);
        organizacaoApiKeyRepository.deleteByIdOrganizacao(idOrganizacao);
        organizacaoFeatureFlagRepository.deleteByIdOrganizacao(idOrganizacao);
        organizacaoConfiguracaoRepository.deleteByIdOrganizacao(idOrganizacao);
        alertaOperacionalRepository.deleteByIdOrganizacao(idOrganizacao);
        auditoriaEventoRepository.deleteByIdOrganizacao(idOrganizacao);

        usuarioOrganizacaoRepository.findByOrganizacaoIdOrganizacaoOrderByUsuarioNmUsuarioAsc(idOrganizacao)
                .forEach(vinculo -> removerVinculoPermanentemente(vinculo));

        organizacaoRepository.delete(organizacao);
    }

    @Transactional
    public void removerUsuarioPermanentemente(Long idOrganizacao, Long idUsuario) {
        organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organizacao nao encontrada"));

        UsuarioOrganizacao vinculo = usuarioOrganizacaoRepository
                .findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacao(idUsuario, idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario da organizacao nao encontrado"));

        if (vinculo.getUsuario().getTpGlobal() == TipoGlobal.SUPER_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nao e permitido remover usuario SUPER_ADMIN");
        }

        removerVinculoPermanentemente(vinculo);
    }

    private void removerVinculoPermanentemente(UsuarioOrganizacao vinculo) {
        Usuario usuario = vinculo.getUsuario();
        Long idUsuario = usuario.getIdUsuario();

        usuarioOrganizacaoRepository.delete(vinculo);

        if (usuarioOrganizacaoRepository.countByUsuarioIdUsuario(idUsuario) == 0) {
            usuarioRepository.delete(usuario);
        }
    }
}
