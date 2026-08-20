package com.notificacao_api.service.queue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoLoteItemRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoLoteRequisicao;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class EnvioLoteSegurancaService {

    private final PropriedadesProtecaoNotificacao propriedades;

    public EnvioLoteSegurancaService(PropriedadesProtecaoNotificacao propriedades) {
        this.propriedades = propriedades;
    }

    public void validarEstruturaLote(EnviarNotificacaoLoteRequisicao requisicao) {
        List<EnviarNotificacaoLoteItemRequisicao> mensagens = requisicao.mensagens();
        if (mensagens == null || mensagens.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lote deve conter ao menos uma mensagem.");
        }

        validarTamanhoLote(mensagens.size());

        if (requisicao.canal() != CanalNotificacao.WHATSAPP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Envio em lote disponivel apenas para WHATSAPP.");
        }

        validarDuplicatasInternas(mensagens);
        validarReferenciasExternasUnicas(mensagens);
    }

    public void validarTamanhoLote(int tamanho) {
        if (tamanho > propriedades.tamanhoMaximoLote()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lote excede o limite operacional de "
                            + propriedades.tamanhoMaximoLote()
                            + " mensagens.");
        }
    }

    private void validarDuplicatasInternas(List<EnviarNotificacaoLoteItemRequisicao> mensagens) {
        Set<String> chaves = new HashSet<>();
        for (EnviarNotificacaoLoteItemRequisicao item : mensagens) {
            String chave = chaveDuplicataInterna(item);
            if (!chaves.add(chave)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Lote contem mensagens duplicadas para o mesmo destinatario e conteudo.");
            }
        }
    }

    private void validarReferenciasExternasUnicas(List<EnviarNotificacaoLoteItemRequisicao> mensagens) {
        Set<String> referencias = new HashSet<>();
        for (EnviarNotificacaoLoteItemRequisicao item : mensagens) {
            if (item.referenciaExterna() == null || item.referenciaExterna().isBlank()) {
                continue;
            }
            String referencia = item.referenciaExterna().trim();
            if (!referencias.add(referencia)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Lote contem referenciaExterna duplicada: " + referencia + ".");
            }
        }
    }

    private String chaveDuplicataInterna(EnviarNotificacaoLoteItemRequisicao item) {
        String destinatario = TelefoneBrasilUtil.normalizarCelularWhatsapp(item.destinatario());
        return destinatario + "|" + item.mensagem().trim();
    }
}
