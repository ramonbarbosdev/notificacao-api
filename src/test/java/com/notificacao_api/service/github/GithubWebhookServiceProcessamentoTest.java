package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;
import com.notificacao_api.service.github.GithubIntegracaoDefaults;
import com.notificacao_api.service.github.GithubIntegracaoSettings;
import com.notificacao_api.service.github.graphql.GithubGraphqlAccessTokenResolver;
import com.notificacao_api.service.github.graphql.GithubGraphqlContentResolver;
import com.notificacao_api.service.github.graphql.GithubProjectV2ContentDetalhes;

@ExtendWith(MockitoExtension.class)
class GithubWebhookServiceProcessamentoTest {

    private static final String PAYLOAD_EDITED = """
            {
              "action": "edited",
              "projects_v2_item": {
                "content_type": "Issue",
                "content_node_id": "I_kwDOSOM5YM8AAAABRzO-Gw"
              },
              "changes": {
                "field_value": {
                  "field_name": "Status",
                  "to": { "name": "Em Andamento" }
                }
              },
              "sender": { "login": "ramonbarbosdev" }
            }
            """;

    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private OrganizacaoConfiguracaoRepository configuracaoRepository;
    @Mock
    private OrganizacaoGithubResponsavelService githubResponsavelService;
    @Mock
    private NotificacaoService notificacaoService;
    @Mock
    private OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    @Mock
    private GithubWebhookWhatsappTemplateService whatsappTemplateService;
    @Mock
    private OrganizacaoGithubIntegracaoSettingsService githubIntegracaoSettingsService;
    @Mock
    private GithubGraphqlAccessTokenResolver githubGraphqlAccessTokenResolver;
    @Mock
    private GithubGraphqlContentResolver githubGraphqlContentResolver;
    @Mock
    private GithubWebhookDecisaoLogService githubWebhookDecisaoLogService;

    private GithubWebhookService service;
    private final GithubIntegracaoSettings integracaoSettings = new GithubIntegracaoSettings(
            GithubIntegracaoDefaults.GRAPHQL_URL,
            GithubIntegracaoDefaults.API_BASE_URL,
            10_000,
            30_000,
            300);

    @BeforeEach
    void setUp() {
        service = new GithubWebhookService(
                featureFlagService,
                configuracaoRepository,
                githubResponsavelService,
                new ObjectMapper(),
                notificacaoService,
                organizacaoConfiguracaoService,
                whatsappTemplateService,
                githubIntegracaoSettingsService,
                githubGraphqlAccessTokenResolver,
                githubGraphqlContentResolver,
                githubWebhookDecisaoLogService);
        lenient().when(githubIntegracaoSettingsService.resolver(any())).thenReturn(integracaoSettings);
        lenient().when(githubGraphqlAccessTokenResolver.resolverBearer(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(githubGraphqlContentResolver.enriquecer(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient()
                .when(whatsappTemplateService.formatar(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    var evento = invocation.getArgument(
                            3, GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados.class);
                    return new GithubWebhookWhatsappTemplateService.MensagemWhatsapp(
                            "GitHub: " + evento.titulo(),
                            "corpo-teste",
                            "corpo-teste");
                });
    }

    @Test
    void projectsV2EditedComSenderOptInEnfileiraWhatsapp() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setDsGithubStatusDisparo(null);
        config.setGithubIgnorarSemResponsavel(false);
        config.setGithubNaoNotificarMovimentador(false);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571999999999"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        service.processar(1L, "projects_v2_item", "delivery-test", PAYLOAD_EDITED);

        verify(notificacaoService).enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void projectsV2SemOptInRegistraNaFila() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setWebhookRegistrarFilaSemDestinatario(true);
        config.setGithubIgnorarSemResponsavel(false);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(config)).thenReturn(true);
        when(notificacaoService.enfileirarGithubSemResponsavel(
                        eq(1L), any(EnviarNotificacaoRequisicao.class), eq(java.util.List.of())))
                .thenReturn(new EnviarNotificacaoResposta(
                        false, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.BLOQUEADA,
                        "sem opt-in", null, null, 0, 3, null, null, null));

        service.processar(1L, "projects_v2_item", "delivery-test", PAYLOAD_EDITED);

        verify(notificacaoService).enfileirarGithubSemResponsavel(
                eq(1L), any(EnviarNotificacaoRequisicao.class), eq(java.util.List.of()));
        verify(notificacaoService, never()).enviarParaOrganizacao(any(), any());
    }

    @Test
    void projectsV2SemOptInComRegistroDesabilitadoNaoEnfileira() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setWebhookRegistrarFilaSemDestinatario(false);
        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(organizacaoConfiguracaoService.deveRegistrarFilaSemDestinatario(config)).thenReturn(false);

        service.processar(1L, "projects_v2_item", "delivery-test", PAYLOAD_EDITED);

        verify(notificacaoService, never()).enfileirarGithubSemResponsavel(any(), any(), any());
        verify(notificacaoService, never()).enviarParaOrganizacao(any(), any());
    }

    @Test
    void payloadSemProjectIgnora() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));

        service.processar(1L, "push", "delivery-test", "{\"action\":\"push\"}");

        verify(notificacaoService, never()).enviarParaOrganizacao(any(), any());
    }

    @Test
    void projectsV2DeletedEnfileiraComOptIn() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubIgnorarSemResponsavel(false);
        config.setGithubNaoNotificarMovimentador(false);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571999999999"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "deleted",
                  "projects_v2_item": { "content_type": "Issue" },
                  "sender": { "login": "ramonbarbosdev" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-del", payload);

        verify(notificacaoService).enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void projectsV2ReorderedSemFieldValueEnfileira() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubNotificarReordenacao(true);
        config.setGithubIgnorarSemResponsavel(false);
        config.setGithubNaoNotificarMovimentador(false);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571999999999"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "reordered",
                  "projects_v2_item": { "content_type": "Issue" },
                  "sender": { "login": "ramonbarbosdev" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-reord", payload);

        verify(notificacaoService).enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void projectsV2CreatedIgnora() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));

        service.processar(
                1L,
                "projects_v2_item",
                "delivery-created",
                "{\"action\":\"created\",\"projects_v2_item\":{},\"sender\":{\"login\":\"x\"}}");

        verify(notificacaoService, never()).enviarParaOrganizacao(any(), any());
    }

    @Test
    void filtroStatusBloqueiaColuna() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setDsGithubStatusDisparo("A Fazer");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));

        service.processar(1L, "projects_v2_item", "delivery-test", PAYLOAD_EDITED);

        verify(notificacaoService, never()).enviarParaOrganizacao(any(), any());
    }

    @Test
    void issueEntraValidacaoInternaNotificaAvaliadores() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubIssueAvisarAvaliadores(true);
        config.setDsGithubIssueStatusDisparo("Validação Interna (Develop)");
        config.setDsGithubPrLoginsAvaliadores("ramonbarbosdev, rayccamell");
        config.setDsGithubStatusDisparo("A Fazer");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571111111111"));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "rayccamell"))
                .thenReturn(Optional.of("5571222222222"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "Issue" },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "from": { "name": "A Fazer" },
                      "to": { "name": "Validação Interna (Develop)" }
                    }
                  },
                  "sender": { "login": "ramonbarbosdev" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-issue-in", payload);

        verify(notificacaoService, org.mockito.Mockito.times(2))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void issueSaiValidacaoInternaParaAFazerNaoDisparaIssueAvaliadores() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubIssueAvisarAvaliadores(true);
        config.setDsGithubIssueStatusDisparo("Validação Interna (Develop)");
        config.setDsGithubPrLoginsAvaliadores("ramonbarbosdev");
        config.setDsGithubStatusDisparo("Validação Interna (Develop)");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "Issue" },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "from": { "name": "Validação Interna (Develop)" },
                      "to": { "name": "A Fazer" }
                    }
                  },
                  "sender": { "login": "ramonbarbosdev" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-issue-out", payload);

        verify(notificacaoService, never()).enviarParaOrganizacao(any(), any());
    }

    @Test
    void issueDevelopAliasNoFiltroNotificaAvaliadores() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubIssueAvisarAvaliadores(true);
        config.setDsGithubIssueStatusDisparo("Develop");
        config.setDsGithubPrLoginsAvaliadores("ramonbarbosdev");
        config.setDsGithubStatusDisparo("A Fazer");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571111111111"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "Issue" },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "to": { "name": "Validação Interna (Develop)" }
                    }
                  },
                  "sender": { "login": "mover" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-issue-develop-alias", payload);

        verify(notificacaoService, org.mockito.Mockito.times(1))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void issueAvaliadoresIgnoraGatilhoStatusAlteradoDesligado() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubNotificarStatusAlterado(false);
        config.setGithubIssueAvisarAvaliadores(true);
        config.setDsGithubIssueStatusDisparo("Validação Interna (Develop)");
        config.setDsGithubPrLoginsAvaliadores("ramonbarbosdev");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571111111111"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "Issue" },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "to": { "name": "Validação Interna (Develop)" }
                    }
                  },
                  "sender": { "login": "mover" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-issue-sem-gatilho-geral", payload);

        verify(notificacaoService, org.mockito.Mockito.times(1))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void pullRequestValidacaoInternaDevelopNotificaAvaliadores() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubPrAvisarAvaliadores(true);
        config.setDsGithubPrStatusDisparo("Validação Interna (Develop)");
        config.setDsGithubPrLoginsAvaliadores("ramonbarbosdev, rayccamell");
        config.setDsGithubStatusDisparo("Outro");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571111111111"));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "rayccamell"))
                .thenReturn(Optional.of("5571222222222"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "PullRequest" },
                  "pull_request": {
                    "title": "feat: exemplo",
                    "html_url": "https://github.com/org/repo/pull/1"
                  },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "to": { "name": "Validação Interna (Develop)" }
                    }
                  },
                  "sender": { "login": "author" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-pr-validacao", payload);

        verify(notificacaoService, org.mockito.Mockito.times(2))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void pullRequestEmRevisaoNotificaLoginsAvaliadores() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubPrAvisarAvaliadores(true);
        config.setDsGithubPrStatusDisparo("Em revisao");
        config.setDsGithubPrLoginsAvaliadores("reviewer1, reviewer2");
        config.setDsGithubStatusDisparo("Outro status");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "reviewer1"))
                .thenReturn(Optional.of("5571111111111"));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "reviewer2"))
                .thenReturn(Optional.of("5571222222222"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "PullRequest" },
                  "pull_request": {
                    "title": "feat: login",
                    "html_url": "https://github.com/org/repo/pull/99"
                  },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "to": { "name": "Em revisão" }
                    }
                  },
                  "sender": { "login": "author" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-pr", payload);

        ArgumentCaptor<String> cenarioCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsappTemplateService).formatar(any(), any(), any(), any(), cenarioCaptor.capture());
        assertEquals(GithubWebhookTemplateCatalog.CENARIO_PR_AVALIADORES, cenarioCaptor.getValue());

        verify(notificacaoService, org.mockito.Mockito.times(2))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void projectsV2GraphqlEnriqueceUrlNoTemplate() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setDsGithubStatusDisparo(null);
        config.setGithubIgnorarSemResponsavel(false);
        config.setGithubNaoNotificarMovimentador(false);
        config.setDsGithubGraphqlTokenEnc("enc");

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubGraphqlAccessTokenResolver.resolverBearer(eq(1L), eq(config), eq(integracaoSettings), any()))
                .thenReturn(Optional.of("ghp_test"));
        when(githubGraphqlContentResolver.enriquecer(
                        eq(1L),
                        eq(integracaoSettings),
                        eq("ghp_test"),
                        eq("I_kwDOSOM5YM8AAAABRzO-Gw"),
                        eq("Issue")))
                .thenReturn(Optional.of(new GithubProjectV2ContentDetalhes(
                        "Issue via GraphQL",
                        "https://github.com/org/repo/issues/7",
                        "Issue",
                        7,
                        List.of())));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571999999999"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        service.processar(1L, "projects_v2_item", "delivery-graphql", PAYLOAD_EDITED);

        ArgumentCaptor<GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados> captor =
                ArgumentCaptor.forClass(GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados.class);
        verify(whatsappTemplateService).formatar(any(), any(), any(), captor.capture(), any());
        assertEquals("https://github.com/org/repo/issues/7", captor.getValue().url());
        assertEquals("Issue via GraphQL", captor.getValue().titulo());
        assertEquals(7, captor.getValue().numero());
    }

    @Test
    void projectsV2GraphqlAssigneesAlimentamResponsaveis() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setDsGithubStatusDisparo(null);
        config.setGithubIgnorarSemResponsavel(true);
        config.setGithubNaoNotificarMovimentador(true);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubGraphqlAccessTokenResolver.resolverBearer(eq(1L), eq(config), eq(integracaoSettings), any()))
                .thenReturn(Optional.of("ghp_test"));
        when(githubGraphqlContentResolver.enriquecer(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new GithubProjectV2ContentDetalhes(
                        "Implementar funcionalidade X",
                        "https://github.com/gpi-organizacao/esimples-api/issues/123",
                        "Issue",
                        123,
                        List.of(
                                new com.notificacao_api.service.github.graphql.GithubGraphqlAssignee("joao", "João Silva"),
                                new com.notificacao_api.service.github.graphql.GithubGraphqlAssignee("maria", "Maria Souza")))));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "joao"))
                .thenReturn(Optional.of("5571111111111"));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "maria"))
                .thenReturn(Optional.of("5571222222222"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        service.processar(1L, "projects_v2_item", "delivery-assignees", PAYLOAD_EDITED);

        ArgumentCaptor<GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados> captor =
                ArgumentCaptor.forClass(GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados.class);
        verify(whatsappTemplateService).formatar(any(), any(), any(), captor.capture(), any());
        assertEquals(List.of("joao", "maria"), captor.getValue().assigneesLogins());
        assertEquals(123, captor.getValue().numero());
        verify(notificacaoService, org.mockito.Mockito.times(2))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }

    @Test
    void extrairOrganizationLoginDoPayload() throws Exception {
        JsonNode root = new ObjectMapper().readTree("""
                { "organization": { "login": "gpi-organizacao" } }
                """);
        assertEquals("gpi-organizacao", GithubWebhookService.extrairOrganizationLogin(root));
    }

    @Test
    void persisteOrganizationLoginNoPrimeiroWebhook() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setDsGithubStatusDisparo(null);
        config.setGithubIgnorarSemResponsavel(false);
        config.setGithubNaoNotificarMovimentador(false);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "ramonbarbosdev"))
                .thenReturn(Optional.of("5571999999999"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "organization": { "login": "gpi-organizacao" },
                  "projects_v2_item": { "content_type": "Issue" },
                  "changes": {
                    "field_value": {
                      "field_name": "Status",
                      "to": { "name": "Em Andamento" }
                    }
                  },
                  "sender": { "login": "ramonbarbosdev" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-org", payload);

        verify(configuracaoRepository).save(config);
        assertEquals("gpi-organizacao", config.getDsGithubOrganizationLogin());
    }

    @Test
    void extrairInstallationIdDoPayload() throws Exception {
        JsonNode root = new ObjectMapper().readTree("""
                { "installation": { "id": 162503134 } }
                """);
        assertEquals(162503134L, GithubWebhookService.extrairInstallationId(root));
        assertNull(GithubWebhookService.extrairInstallationId(new ObjectMapper().readTree("{}")));
    }

    @Test
    void pullRequestStatusForaDaListaPrUsaFluxoNormal() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setIdOrganizacao(1L);
        config.setGithubPrAvisarAvaliadores(true);
        config.setDsGithubPrStatusDisparo("Em revisao");
        config.setDsGithubPrLoginsAvaliadores("reviewer1");
        config.setGithubIgnorarSemResponsavel(false);
        config.setGithubNaoNotificarMovimentador(false);

        when(configuracaoRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(config));
        when(githubResponsavelService.buscarWhatsappPorLogin(1L, "author"))
                .thenReturn(Optional.of("5571999999999"));
        when(notificacaoService.enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class)))
                .thenReturn(new EnviarNotificacaoResposta(
                        true, 1L, CanalNotificacao.WHATSAPP, StatusNotificacao.PENDENTE,
                        null, null, null, 0, 3, null, null, null));

        String payload = """
                {
                  "action": "edited",
                  "projects_v2_item": { "content_type": "PullRequest" },
                  "pull_request": { "title": "PR X", "html_url": "https://github.com/o/r/pull/1" },
                  "changes": {
                    "field_value": { "to": { "name": "Em Andamento" } }
                  },
                  "sender": { "login": "author" }
                }
                """;

        service.processar(1L, "projects_v2_item", "delivery-pr2", payload);

        verify(githubResponsavelService, never()).buscarWhatsappPorLogin(1L, "reviewer1");
        verify(notificacaoService).enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
    }
}
