package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.dto.integracao.github.GithubIntegracaoProjectsV2PatchRequest;
import com.notificacao_api.enums.GithubIntegracaoModulo;
import com.notificacao_api.model.github.GithubOrganizacaoConfig;
import com.notificacao_api.model.github.OrganizacaoGithubModulo;
import com.notificacao_api.repository.OrganizacaoGithubModuloRepository;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.OrganizacaoGithubAppCredentialsService;
import com.notificacao_api.service.OrganizacaoGithubGraphqlTokenService;
import com.notificacao_api.service.OrganizacaoGithubIntegracaoSettingsService;

@ExtendWith(MockitoExtension.class)
class GithubIntegracaoAppServiceTest {

    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private GithubIntegracaoConfigService githubIntegracaoConfigService;
    @Mock
    private OrganizacaoGithubModuloRepository moduloRepository;
    @Mock
    private OrganizacaoGithubGraphqlTokenService graphqlTokenService;
    @Mock
    private OrganizacaoGithubAppCredentialsService appCredentialsService;
    @Mock
    private OrganizacaoGithubIntegracaoSettingsService integracaoSettingsService;
    @Mock
    private GithubWebhookTemplatesPorCenarioService templatesPorCenarioService;

    private GithubIntegracaoAppService service;

    @BeforeEach
    void setUp() {
        service = new GithubIntegracaoAppService(
                featureFlagService,
                githubIntegracaoConfigService,
                moduloRepository,
                graphqlTokenService,
                appCredentialsService,
                integracaoSettingsService,
                templatesPorCenarioService,
                new ObjectMapper());
    }

    @Test
    void patchProjectsV2DelegaParaConfigService() {
        Long idOrganizacao = 7L;
        GithubOrganizacaoConfig atual = new GithubOrganizacaoConfig();
        atual.setIdOrganizacao(idOrganizacao);
        GithubOrganizacaoConfig salvo = new GithubOrganizacaoConfig();
        salvo.setIdOrganizacao(idOrganizacao);
        salvo.setDsGithubStatusDisparo("Em Andamento");

        when(githubIntegracaoConfigService.obterConfiguracao(idOrganizacao)).thenReturn(atual);
        when(githubIntegracaoConfigService.salvarProjectsV2(eq(idOrganizacao), any())).thenReturn(salvo);
        OrganizacaoGithubModulo modulo = new OrganizacaoGithubModulo();
        modulo.setFlHabilitado(true);
        modulo.setDsModulo(GithubIntegracaoModulo.PROJECTS_V2.codigo());
        when(moduloRepository.findByIdOrganizacaoAndDsModulo(
                        idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2.codigo()))
                .thenReturn(java.util.Optional.of(modulo));
        when(templatesPorCenarioService.ler(salvo)).thenReturn(java.util.Map.of());

        var response = service.patchProjectsV2(
                idOrganizacao,
                new GithubIntegracaoProjectsV2PatchRequest(
                        null,
                        null,
                        "Em Andamento",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertEquals("Em Andamento", response.dsGithubStatusDisparo());
        verify(githubIntegracaoConfigService).salvarProjectsV2(eq(idOrganizacao), any(GithubOrganizacaoConfig.class));
    }

    @Test
    void patchModuloHabilitadoAtualizaFlag() {
        Long idOrganizacao = 3L;
        OrganizacaoGithubModulo modulo = new OrganizacaoGithubModulo();
        modulo.setDsModulo(GithubIntegracaoModulo.PROJECTS_V2.codigo());
        modulo.setFlHabilitado(false);
        when(moduloRepository.findByIdOrganizacaoAndDsModulo(
                        idOrganizacao, GithubIntegracaoModulo.PROJECTS_V2.codigo()))
                .thenReturn(java.util.Optional.of(modulo));
        when(moduloRepository.save(modulo)).thenReturn(modulo);

        var response = service.patchModuloHabilitado(idOrganizacao, "PROJECTS_V2", true);

        assertTrue(modulo.getFlHabilitado());
        assertTrue(response.habilitado());
        assertEquals("PROJECTS_V2", response.codigo());
        verify(githubIntegracaoConfigService).garantirRegistros(idOrganizacao);
    }

    @Test
    void patchModuloHabilitadoDesligaModulo() {
        Long idOrganizacao = 3L;
        OrganizacaoGithubModulo modulo = new OrganizacaoGithubModulo();
        modulo.setDsModulo(GithubIntegracaoModulo.ISSUE_COMMENT.codigo());
        modulo.setFlHabilitado(true);
        when(moduloRepository.findByIdOrganizacaoAndDsModulo(
                        idOrganizacao, GithubIntegracaoModulo.ISSUE_COMMENT.codigo()))
                .thenReturn(java.util.Optional.of(modulo));
        when(moduloRepository.save(modulo)).thenReturn(modulo);

        var response = service.patchModuloHabilitado(idOrganizacao, "ISSUE_COMMENT", false);

        assertFalse(modulo.getFlHabilitado());
        assertFalse(response.habilitado());
        verify(githubIntegracaoConfigService).garantirRegistros(idOrganizacao);
    }
}
