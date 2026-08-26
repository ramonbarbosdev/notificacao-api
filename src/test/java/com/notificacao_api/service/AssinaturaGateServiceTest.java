package com.notificacao_api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.StatusAssinatura;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.OrganizacaoAssinatura;
import com.notificacao_api.model.Plano;
import com.notificacao_api.repository.OrganizacaoAssinaturaRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.PlanoRepository;

@ExtendWith(MockitoExtension.class)
class AssinaturaGateServiceTest {

    @Mock
    private OrganizacaoRepository organizacaoRepository;
    @Mock
    private OrganizacaoAssinaturaRepository assinaturaRepository;
    @Mock
    private PlanoRepository planoRepository;

    @InjectMocks
    private AssinaturaGateService gateService;

    @Test
    void devePermitirPlanoGratuito() {
        Organizacao org = organizacao(1L, 1L);
        Plano plano = plano(1L, BigDecimal.ZERO);
        when(organizacaoRepository.findById(1L)).thenReturn(Optional.of(org));
        when(planoRepository.findById(1L)).thenReturn(Optional.of(plano));

        assertDoesNotThrow(() -> gateService.validarOrganizacaoAtiva(1L));
    }

    @Test
    void deveBloquearAssinaturaInadimplente() {
        Organizacao org = organizacao(1L, 2L);
        Plano plano = plano(2L, new BigDecimal("49.90"));
        OrganizacaoAssinatura assinatura = new OrganizacaoAssinatura();
        assinatura.setStatus(StatusAssinatura.INADIMPLENTE);

        when(organizacaoRepository.findById(1L)).thenReturn(Optional.of(org));
        when(planoRepository.findById(2L)).thenReturn(Optional.of(plano));
        when(assinaturaRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(assinatura));

        assertThrows(ResponseStatusException.class, () -> gateService.validarOrganizacaoAtiva(1L));
    }

    @Test
    void devePermitirTrialValido() {
        Organizacao org = organizacao(1L, 2L);
        Plano plano = plano(2L, new BigDecimal("49.90"));
        OrganizacaoAssinatura assinatura = new OrganizacaoAssinatura();
        assinatura.setStatus(StatusAssinatura.TRIAL);
        assinatura.setDtFimTrial(LocalDateTime.now().plusDays(5));

        when(organizacaoRepository.findById(1L)).thenReturn(Optional.of(org));
        when(planoRepository.findById(2L)).thenReturn(Optional.of(plano));
        when(assinaturaRepository.findByIdOrganizacao(1L)).thenReturn(Optional.of(assinatura));

        assertDoesNotThrow(() -> gateService.validarOrganizacaoAtiva(1L));
    }

    private Organizacao organizacao(Long id, Long idPlano) {
        Organizacao org = new Organizacao();
        org.setIdOrganizacao(id);
        org.setIdPlano(idPlano);
        org.setNmOrganizacao("Org Teste");
        org.setFlAtivo(true);
        return org;
    }

    private Plano plano(Long id, BigDecimal valor) {
        Plano plano = new Plano();
        plano.setIdPlano(id);
        plano.setNmPlano("Plano");
        plano.setVlMensal(valor);
        plano.setFlAtivo(true);
        return plano;
    }
}
