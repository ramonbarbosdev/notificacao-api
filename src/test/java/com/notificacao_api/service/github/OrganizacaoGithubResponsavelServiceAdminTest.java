package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.model.OrganizacaoGithubResponsavel;
import com.notificacao_api.repository.OrganizacaoGithubResponsavelRepository;
import com.notificacao_api.repository.WhatsappConversaRepository;
import com.notificacao_api.service.AuditoriaEventoService;
import com.notificacao_api.service.FeatureFlagService;
import com.notificacao_api.service.OrganizacaoConfiguracaoService;
import com.notificacao_api.service.whatsapp.WhatsappSessaoService;

@ExtendWith(MockitoExtension.class)
class OrganizacaoGithubResponsavelServiceAdminTest {

    @Mock
    private OrganizacaoGithubResponsavelRepository repository;
    @Mock
    private WhatsappConversaRepository whatsappConversaRepository;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    @Mock
    private WhatsappSessaoService whatsappSessaoService;
    @Mock
    private AuditoriaEventoService auditoriaEventoService;

    private OrganizacaoGithubResponsavelService service;

    @BeforeEach
    void setUp() {
        service = new OrganizacaoGithubResponsavelService(
                repository,
                whatsappConversaRepository,
                featureFlagService,
                organizacaoConfiguracaoService,
                whatsappSessaoService,
                auditoriaEventoService);
    }

    @Test
    void desativaResponsavelDaOrganizacao() {
        OrganizacaoGithubResponsavel row = new OrganizacaoGithubResponsavel();
        row.setIdGithubResponsavel(10L);
        row.setIdOrganizacao(1L);
        row.setDsGithubLogin("dev1");
        row.setNuWhatsapp("5571999999999");
        row.setAtivo(true);
        when(repository.findByIdGithubResponsavelAndIdOrganizacao(10L, 1L)).thenReturn(Optional.of(row));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resposta = service.atualizarAtivo(1L, 10L, false);

        assertFalse(resposta.habilitado());
        assertFalse(resposta.ativo());
        verify(repository).save(row);
    }

    @Test
    void excluirInexistenteRetorna404() {
        when(repository.findByIdGithubResponsavelAndIdOrganizacao(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.excluir(1L, 99L));
    }
}
