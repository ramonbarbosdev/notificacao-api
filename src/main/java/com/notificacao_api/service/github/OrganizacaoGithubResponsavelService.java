package com.notificacao_api.service.github;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class OrganizacaoGithubResponsavelService {

    private static final Logger log = LoggerFactory.getLogger(OrganizacaoGithubResponsavelService.class);

    private final OrganizacaoGithubResponsavelRepository repository;
    private final FeatureFlagService featureFlagService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final WhatsappSessaoService whatsappSessaoService;

    public OrganizacaoGithubResponsavelService(
            OrganizacaoGithubResponsavelRepository repository,
            FeatureFlagService featureFlagService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            WhatsappSessaoService whatsappSessaoService) {
        this.repository = repository;
        this.featureFlagService = featureFlagService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.whatsappSessaoService = whatsappSessaoService;
    }

    @Transactional
    public void processarOptInInbound(WhatsappInboundRequest request) {
        Long idOrganizacao = request.idOrganizacao();
        if (!featureFlagService.estaHabilitado(idOrganizacao, RecursoFeature.GITHUB_WEBHOOK)) {
            return;
        }

        String texto = request.preview();
        if (texto == null || texto.isBlank()) {
            return;
        }

        String telefone = request.telefone();

        String fraseAtivacao = organizacaoConfiguracaoService.fraseAtivacaoGithubWhatsapp(idOrganizacao);
        if (GithubWhatsappOptInSupport.correspondeFraseAtivacao(texto, fraseAtivacao)) {
            loginAtivoPorTelefone(idOrganizacao, telefone)
                    .ifPresentOrElse(
                            login -> enviarRespostaWhatsapp(
                                    idOrganizacao,
                                    telefone,
                                    "Voce ja recebe notificacoes do GitHub como @" + login + "."),
                            () -> {
                                registrarOptinPendente(idOrganizacao, telefone);
                                enviarRespostaWhatsapp(
                                        idOrganizacao,
                                        telefone,
                                        "Ótimo! Responda com seu usuário do GitHub (ex.: octocat) para vincular este WhatsApp "
                                                + "e receber alertas das issues em que voce for assignee.");
                            });
            return;
        }

        if (!aguardandoLoginGithub(idOrganizacao, telefone)) {
            return;
        }

        String login = GithubWhatsappOptInSupport.extrairLoginGithub(texto);
        if (login == null) {
            enviarRespostaWhatsapp(
                    idOrganizacao,
                    telefone,
                    "Usuario invalido. Envie apenas o login do GitHub (letras, numeros e hifen), "
                            + "por exemplo: octocat");
            return;
        }

        ativarPorWhatsapp(idOrganizacao, login, telefone);
        enviarRespostaWhatsapp(
                idOrganizacao,
                telefone,
                "Notificacoes do GitHub ativadas para o usuario @" + login + ". "
                        + "Voce recebera mensagens quando for assignee de uma issue.");
        log.info("GitHub WhatsApp opt-in concluido org={} login={} telefone={}", idOrganizacao, login, telefone);
    }

    @Transactional(readOnly = true)
    public Optional<String> loginAtivoPorTelefone(Long idOrganizacao, String nuWhatsapp) {
        return repository
                .findByIdOrganizacaoAndNuWhatsapp(idOrganizacao, normalizarWhatsapp(nuWhatsapp))
                .filter(this::cadastroCompleto)
                .map(OrganizacaoGithubResponsavel::getDsGithubLogin);
    }

    @Transactional
    public void registrarOptinPendente(Long idOrganizacao, String nuWhatsapp) {
        String whatsapp = normalizarWhatsapp(nuWhatsapp);
        OrganizacaoGithubResponsavel row = repository
                .findByIdOrganizacaoAndNuWhatsapp(idOrganizacao, whatsapp)
                .orElseGet(OrganizacaoGithubResponsavel::new);

        row.setIdOrganizacao(idOrganizacao);
        row.setNuWhatsapp(whatsapp);
        row.setDsGithubLogin(null);
        row.setAtivo(true);
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public boolean aguardandoLoginGithub(Long idOrganizacao, String nuWhatsapp) {
        return repository
                .findByIdOrganizacaoAndNuWhatsappAndDsGithubLoginIsNull(
                        idOrganizacao, normalizarWhatsapp(nuWhatsapp))
                .isPresent();
    }

    @Transactional
    public void ativarPorWhatsapp(Long idOrganizacao, String githubLogin, String nuWhatsapp) {
        String login = normalizarLogin(githubLogin);
        String whatsapp = normalizarWhatsapp(nuWhatsapp);

        Optional<OrganizacaoGithubResponsavel> porLogin =
                repository.findByIdOrganizacaoAndDsGithubLoginIgnoreCase(idOrganizacao, login);
        Optional<OrganizacaoGithubResponsavel> porTelefone =
                repository.findByIdOrganizacaoAndNuWhatsapp(idOrganizacao, whatsapp);

        if (porLogin.isPresent()) {
            OrganizacaoGithubResponsavel existenteLogin = porLogin.get();
            existenteLogin.setNuWhatsapp(whatsapp);
            existenteLogin.setDsGithubLogin(login);
            existenteLogin.setAtivo(true);
            repository.save(existenteLogin);
            porTelefone
                    .filter(outro -> !outro.getIdGithubResponsavel().equals(existenteLogin.getIdGithubResponsavel()))
                    .ifPresent(repository::delete);
            return;
        }

        OrganizacaoGithubResponsavel row = porTelefone.orElseGet(OrganizacaoGithubResponsavel::new);
        row.setIdOrganizacao(idOrganizacao);
        row.setNuWhatsapp(whatsapp);
        row.setDsGithubLogin(login);
        row.setAtivo(true);
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public Optional<String> buscarWhatsappPorLogin(Long idOrganizacao, String githubLogin) {
        if (githubLogin == null || githubLogin.isBlank()) {
            return Optional.empty();
        }
        return repository
                .findByIdOrganizacaoAndDsGithubLoginIgnoreCaseAndAtivoTrue(
                        idOrganizacao, normalizarLogin(githubLogin))
                .filter(this::cadastroCompleto)
                .map(OrganizacaoGithubResponsavel::getNuWhatsapp);
    }

    private boolean cadastroCompleto(OrganizacaoGithubResponsavel row) {
        return Boolean.TRUE.equals(row.getAtivo())
                && row.getDsGithubLogin() != null
                && !row.getDsGithubLogin().isBlank();
    }

    private String normalizarLogin(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizarWhatsapp(String telefone) {
        String normalizado = TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, telefone);
        if (normalizado == null || normalizado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WhatsApp invalido.");
        }
        return normalizado;
    }

    private void enviarRespostaWhatsapp(Long idOrganizacao, String telefone, String mensagem) {
        try {
            whatsappSessaoService.enviarMensagemDaOrganizacao(
                    idOrganizacao, new EnviarMensagemWhatsappRequisicao(telefone, mensagem));
        } catch (Exception ex) {
            log.warn(
                    "Falha ao enviar resposta opt-in GitHub org={} telefone={} erro={}",
                    idOrganizacao,
                    telefone,
                    ex.getMessage());
        }
    }
}
