package com.notificacao_api.service.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GithubWhatsappOptInSupportTest {

    @Test
    void reconheceFraseAtivacaoComAcentosEPontuacao() {
        String frase = GithubWhatsappOptInSupport.FRASE_ATIVACAO_PADRAO;
        assertTrue(GithubWhatsappOptInSupport.correspondeFraseAtivacao(frase, frase));
        assertTrue(GithubWhatsappOptInSupport.correspondeFraseAtivacao(
                "quero receber notificacao, do github!", frase));
    }

    @Test
    void reconheceFrasePersonalizada() {
        String custom = "Ativar alertas do meu repo!";
        assertTrue(GithubWhatsappOptInSupport.correspondeFraseAtivacao(custom, custom));
        assertFalse(GithubWhatsappOptInSupport.correspondeFraseAtivacao("oi", custom));
    }

    @Test
    void usaPadraoQuandoConfigNula() {
        assertEquals(
                GithubWhatsappOptInSupport.FRASE_ATIVACAO_PADRAO,
                GithubWhatsappOptInSupport.resolverFraseAtivacao(null));
    }

    @Test
    void montaLinkWaMe() {
        String link = GithubWhatsappOptInSupport.montarLinkWaMe(
                "+55 (71) 99118-0200", GithubWhatsappOptInSupport.FRASE_ATIVACAO_PADRAO);
        assertTrue(link.startsWith("https://wa.me/5571991180200?text="));
    }

    @Test
    void extraiLoginGithub() {
        assertEquals("octocat", GithubWhatsappOptInSupport.extrairLoginGithub("@octocat"));
        assertEquals("my-user", GithubWhatsappOptInSupport.extrairLoginGithub("my-user"));
    }
}
