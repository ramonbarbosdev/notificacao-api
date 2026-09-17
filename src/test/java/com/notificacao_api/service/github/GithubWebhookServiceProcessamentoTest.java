package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.NotificacaoService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;

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

    private GithubWebhookService service;

    @BeforeEach
    void setUp() {
        service = new GithubWebhookService(
                featureFlagService,
                configuracaoRepository,
                githubResponsavelService,
                new ObjectMapper(),
                notificacaoService,
                organizacaoConfiguracaoService,
                whatsappTemplateService);
        lenient().when(whatsappTemplateService.formatar(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    var evento = invocation.getArgument(3, GithubWebhookWhatsappTemplateService.GithubWebhookEventoDados.class);
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

        verify(notificacaoService, org.mockito.Mockito.times(2))
                .enviarParaOrganizacao(eq(1L), any(EnviarNotificacaoRequisicao.class));
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
