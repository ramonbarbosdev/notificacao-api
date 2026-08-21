package com.notificacao_api.service.whatsapp;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notificacao_api.enums.WhatsappMensagemDirecao;
import com.notificacao_api.enums.WhatsappMensagemStatus;
import com.notificacao_api.enums.WhatsappMensagemTipo;
import com.notificacao_api.enums.WhatsappProvedorEnvio;
import com.notificacao_api.model.WhatsappMensagem;
import com.notificacao_api.repository.WhatsappMensagemRepository;
import com.notificacao_api.service.whatsapp.provider.ResultadoEnvioWhatsapp;

@Service
public class WhatsappMensagemService {

    private final WhatsappMensagemRepository repository;

    public WhatsappMensagemService(WhatsappMensagemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WhatsappMensagem registrarEnvioOutbound(
            Long idOrganizacao,
            Long idNotificacao,
            String telefone,
            WhatsappMensagemTipo tipo,
            String templateName,
            ResultadoEnvioWhatsapp resultado) {
        WhatsappMensagem mensagem = new WhatsappMensagem();
        mensagem.setIdOrganizacao(idOrganizacao);
        mensagem.setIdNotificacao(idNotificacao);
        mensagem.setProvider(WhatsappProvedorEnvio.META_CLOUD);
        mensagem.setTelefone(telefone);
        mensagem.setDirecao(WhatsappMensagemDirecao.OUTBOUND);
        mensagem.setTipo(tipo);
        mensagem.setTemplateName(templateName);
        mensagem.setIdExterno(resultado.externalMessageId());
        mensagem.setStatus(resultado.status());
        mensagem.setErro(resultado.erro());
        if (resultado.confirmado() || resultado.externalMessageId() != null) {
            mensagem.setDtEnvio(LocalDateTime.now());
        }
        return repository.save(mensagem);
    }

    @Transactional
    public void atualizarStatusPorIdExterno(String idExterno, WhatsappMensagemStatus status, String codigoErro, String erro) {
        repository.findByIdExterno(idExterno).ifPresent(mensagem -> {
            mensagem.setStatus(status);
            mensagem.setCodigoErro(codigoErro);
            if (erro != null) {
                mensagem.setErro(erro);
            }
            LocalDateTime agora = LocalDateTime.now();
            switch (status) {
                case SENT -> mensagem.setDtEnvio(agora);
                case DELIVERED -> mensagem.setDtEntrega(agora);
                case READ -> mensagem.setDtLeitura(agora);
                case FAILED -> {
                    if (mensagem.getDtEnvio() == null) {
                        mensagem.setDtEnvio(agora);
                    }
                }
                default -> {
                }
            }
            repository.save(mensagem);
        });
    }
}
