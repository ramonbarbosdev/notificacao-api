package com.notificacao_api.service.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;
import com.notificacao_api.repository.WhatsappSessionRepository;

@ExtendWith(MockitoExtension.class)
class ProtecaoNotificacaoServiceTest {

    @Mock
    private PropriedadesProtecaoNotificacao propriedades;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private WhatsappSessionRepository whatsappSessionRepository;

    @Mock
    private OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository;

    @Mock
    private SegurancaOperacionalWhatsappService segurancaOperacionalWhatsappService;

    private ProtecaoNotificacaoService service;

    @BeforeEach
    void setUp() {
        service = new ProtecaoNotificacaoService(
                propriedades,
                notificacaoRepository,
                whatsappSessionRepository,
                organizacaoConfiguracaoRepository,
                segurancaOperacionalWhatsappService);
    }

    @Test
    void gerarHashDeduplicacao_mesmaMensagemSemReferencia_produzMesmoHash() {
        String hash1 = service.gerarHashDeduplicacao(
                1L, CanalNotificacao.WHATSAPP, "71999999999", "Informe a data:", null);
        String hash2 = service.gerarHashDeduplicacao(
                1L, CanalNotificacao.WHATSAPP, "71999999999", "Informe a data:", null);

        assertEquals(hash1, hash2);
    }

    @Test
    void gerarHashDeduplicacao_mesmaMensagemComReferenciasDiferentes_produzHashesDiferentes() {
        String hash1 = service.gerarHashDeduplicacao(
                1L, CanalNotificacao.WHATSAPP, "71999999999", "Informe a data:", "he-bot:1:DATA:msg-1");
        String hash2 = service.gerarHashDeduplicacao(
                1L, CanalNotificacao.WHATSAPP, "71999999999", "Informe a data:", "he-bot:1:DATA:msg-2");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void delayMedioSegundos_usaConfiguracaoDaOrganizacao() {
        OrganizacaoConfiguracao config = new OrganizacaoConfiguracao();
        config.setWhatsappDelayMinSegundos(1);
        config.setWhatsappDelayMaxSegundos(3);

        when(organizacaoConfiguracaoRepository.findByIdOrganizacao(7L)).thenReturn(Optional.of(config));

        assertEquals(1L, service.delayMinimoSegundos(7L));
        assertEquals(3L, service.delayMaximoSegundos(7L));
        assertEquals(2.0, service.delayMedioSegundos(7L));
    }

    @Test
    void gerarHashDeduplicacao_referenciaEmBranco_ignoraReferencia() {
        String semReferencia = service.gerarHashDeduplicacao(
                1L, CanalNotificacao.WHATSAPP, "71999999999", "Informe a data:", null);
        String referenciaEmBranco = service.gerarHashDeduplicacao(
                1L, CanalNotificacao.WHATSAPP, "71999999999", "Informe a data:", "   ");

        assertEquals(semReferencia, referenciaEmBranco);
    }
}
