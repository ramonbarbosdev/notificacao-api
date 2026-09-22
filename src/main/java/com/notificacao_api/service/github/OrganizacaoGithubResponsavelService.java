package com.notificacao_api.service.github;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.integracao.GithubResponsavelResponse;
import com.notificacao_api.dto.whatsapp.EnviarMensagemWhatsappRequisicao;
import com.notificacao_api.dto.whatsapp.WhatsappInboundRequest;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.RecursoFeature;
import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.model.WhatsappConversa;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.repository.WhatsappConversaRepository;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class OrganizacaoGithubResponsavelService {

    private static final Logger log = LoggerFactory.getLogger(OrganizacaoGithubResponsavelService.class);

    private final OrganizacaoGithubResponsavelRepository repository;
    private final WhatsappConversaRepository whatsappConversaRepository;
    private final FeatureFlagService featureFlagService;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final WhatsappSessaoService whatsappSessaoService;
    private final AuditoriaEventoService auditoriaEventoService;

    public OrganizacaoGithubResponsavelService(
            OrganizacaoGithubResponsavelRepository repository,
            WhatsappConversaRepository whatsappConversaRepository,
            FeatureFlagService featureFlagService,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            WhatsappSessaoService whatsappSessaoService,
            AuditoriaEventoService auditoriaEventoService) {
        this.repository = repository;
        this.whatsappConversaRepository = whatsappConversaRepository;
        this.featureFlagService = featureFlagService;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.whatsappSessaoService = whatsappSessaoService;
        this.auditoriaEventoService = auditoriaEventoService;
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
            registrarMudancaWhatsappSeNecessario(idOrganizacao, existenteLogin, whatsapp);
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
    public List<GithubResponsavelResponse> listarPorOrganizacao(Long idOrganizacao) {
        return repository.findByIdOrganizacaoOrderByDtAtualizacaoDesc(idOrganizacao).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GithubResponsavelResponse atualizarAtivo(Long idOrganizacao, Long idGithubResponsavel, boolean ativo) {
        OrganizacaoGithubResponsavel row = buscarDaOrganizacao(idOrganizacao, idGithubResponsavel);
        row.setAtivo(ativo);
        repository.save(row);
        auditoriaEventoService.registrarSistema(
                idOrganizacao,
                "GITHUB_RESPONSAVEL",
                ativo ? "REATIVAR" : "DESATIVAR",
                ativo
                        ? "Admin reativou opt-in GitHub para @" + row.getDsGithubLogin()
                        : "Admin desativou opt-in GitHub para @" + row.getDsGithubLogin()
                                + " — o WhatsApp " + mascararWhatsapp(row.getNuWhatsapp())
                                + " deixa de receber alertas.",
                null,
                Map.of(
                        "idGithubResponsavel",
                        idGithubResponsavel,
                        "githubLogin",
                        row.getDsGithubLogin(),
                        "ativo",
                        ativo));
        log.info(
                "GitHub responsavel {} org={} id={} login={}",
                ativo ? "reativado" : "desativado",
                idOrganizacao,
                idGithubResponsavel,
                row.getDsGithubLogin());
        return toResponse(row);
    }

    @Transactional
    public void excluir(Long idOrganizacao, Long idGithubResponsavel) {
        OrganizacaoGithubResponsavel row = buscarDaOrganizacao(idOrganizacao, idGithubResponsavel);
        auditoriaEventoService.registrarSistema(
                idOrganizacao,
                "GITHUB_RESPONSAVEL",
                "EXCLUIR",
                "Admin excluiu vínculo GitHub @" + row.getDsGithubLogin() + " — não recebe mais alertas até novo opt-in.",
                Map.of(
                        "githubLogin",
                        row.getDsGithubLogin(),
                        "whatsappMascarado",
                        mascararWhatsapp(row.getNuWhatsapp())),
                null);
        repository.delete(row);
        log.info(
                "GitHub responsavel excluido org={} id={} login={}",
                idOrganizacao,
                idGithubResponsavel,
                row.getDsGithubLogin());
    }

    private OrganizacaoGithubResponsavel buscarDaOrganizacao(Long idOrganizacao, Long idGithubResponsavel) {
        return repository
                .findByIdGithubResponsavelAndIdOrganizacao(idGithubResponsavel, idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsavel GitHub nao encontrado."));
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

    /**
     * Resolve telefones com opt-in e nome para personalizar templates (nome do contato no WhatsApp ou login).
     */
    @Transactional(readOnly = true)
    public List<GithubWhatsappDestinatario> resolverDestinatariosWhatsapp(
            Long idOrganizacao, List<String> loginsResponsaveis) {
        if (loginsResponsaveis == null || loginsResponsaveis.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, GithubWhatsappDestinatario> porTelefone = new LinkedHashMap<>();
        for (String loginBruto : loginsResponsaveis) {
            if (!StringUtils.hasText(loginBruto)) {
                continue;
            }
            String login = normalizarLogin(loginBruto);
            buscarWhatsappPorLogin(idOrganizacao, login).ifPresent(telefoneBruto -> {
                String telefone =
                        TelefoneBrasilUtil.normalizarDestino(CanalNotificacao.WHATSAPP, telefoneBruto.trim());
                if (!StringUtils.hasText(telefone) || porTelefone.containsKey(telefone)) {
                    return;
                }
                String nome = resolverNomeExibicaoDestinatario(idOrganizacao, telefone, login);
                porTelefone.put(telefone, new GithubWhatsappDestinatario(telefone, login, nome));
            });
        }
        return List.copyOf(porTelefone.values());
    }

    String resolverNomeExibicaoDestinatario(Long idOrganizacao, String telefoneNormalizado, String githubLogin) {
        Optional<String> doContato = whatsappConversaRepository
                .findByIdOrganizacaoAndTelefone(idOrganizacao, telefoneNormalizado)
                .map(WhatsappConversa::getNmContato)
                .filter(StringUtils::hasText)
                .map(OrganizacaoGithubResponsavelService::extrairPrimeiroNomeContato)
                .filter(StringUtils::hasText);
        if (doContato.isPresent()) {
            return doContato.get();
        }
        return githubLogin != null ? githubLogin : "";
    }

    private static String extrairPrimeiroNomeContato(String nmContato) {
        String texto = nmContato.trim();
        if (texto.isEmpty()) {
            return "";
        }
        int espaco = texto.indexOf(' ');
        if (espaco > 0) {
            return texto.substring(0, espaco);
        }
        return texto;
    }

    private boolean cadastroCompleto(OrganizacaoGithubResponsavel row) {
        return Boolean.TRUE.equals(row.getAtivo())
                && row.getDsGithubLogin() != null
                && !row.getDsGithubLogin().isBlank();
    }

    private GithubResponsavelResponse toResponse(OrganizacaoGithubResponsavel row) {
        return new GithubResponsavelResponse(
                row.getIdGithubResponsavel(),
                row.getDsGithubLogin(),
                mascararWhatsapp(row.getNuWhatsapp()),
                cadastroCompleto(row),
                Boolean.TRUE.equals(row.getAtivo()),
                row.getDtAtualizacao(),
                mascararWhatsapp(row.getNuWhatsappAnterior()),
                row.getDtMudancaWhatsapp(),
                montarAlertaAdministrador(row));
    }

    private void registrarMudancaWhatsappSeNecessario(
            Long idOrganizacao, OrganizacaoGithubResponsavel row, String whatsappNovo) {
        String atual = row.getNuWhatsapp();
        if (atual == null || atual.isBlank() || atual.equals(whatsappNovo)) {
            return;
        }
        row.setNuWhatsappAnterior(atual);
        row.setDtMudancaWhatsapp(LocalDateTime.now());
        auditoriaEventoService.registrarSistema(
                idOrganizacao,
                "GITHUB_RESPONSAVEL",
                "WHATSAPP_ALTERADO",
                "Opt-in GitHub @" + row.getDsGithubLogin() + ": WhatsApp alterado de "
                        + mascararWhatsapp(atual) + " para " + mascararWhatsapp(whatsappNovo)
                        + ". O número anterior não recebe mais alertas deste login.",
                Map.of("whatsappAnterior", mascararWhatsapp(atual), "whatsappNovo", mascararWhatsapp(whatsappNovo)),
                Map.of("githubLogin", row.getDsGithubLogin()));
    }

    private String montarAlertaAdministrador(OrganizacaoGithubResponsavel row) {
        if (row.getDsGithubLogin() != null && !Boolean.TRUE.equals(row.getAtivo())) {
            return "Desativado — o WhatsApp " + mascararWhatsapp(row.getNuWhatsapp())
                    + " não recebe alertas GitHub deste login até reativar ou novo opt-in.";
        }
        if (row.getNuWhatsappAnterior() != null
                && !row.getNuWhatsappAnterior().isBlank()
                && row.getDtMudancaWhatsapp() != null) {
            return "WhatsApp do login atualizado: agora " + mascararWhatsapp(row.getNuWhatsapp())
                    + ". O número " + mascararWhatsapp(row.getNuWhatsappAnterior())
                    + " não recebe mais notificações deste @"
                    + row.getDsGithubLogin()
                    + ".";
        }
        return null;
    }

    private static String mascararWhatsapp(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return "—";
        }
        String digits = telefone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*****" + digits.substring(digits.length() - 4);
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
