package com.notificacao_api.service;

import org.springframework.stereotype.Service;

import com.notificacao_api.enums.EventoAuditoriaNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.AuditoriaNotificacao;
import com.notificacao_api.repository.AuditoriaNotificacaoRepository;

@Service
public class AuditoriaNotificacaoService {

    private final AuditoriaNotificacaoRepository auditoriaRepository;

    public AuditoriaNotificacaoService(AuditoriaNotificacaoRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    public void registrar(Notificacao notificacao, EventoAuditoriaNotificacao evento, String erro) {
        AuditoriaNotificacao auditoria = new AuditoriaNotificacao();
        auditoria.setIdNotificacao(notificacao.getIdNotificacao());
        auditoria.setIdOrganizacao(notificacao.getIdOrganizacao());
        auditoria.setCanal(notificacao.getCanal());
        auditoria.setDestinatario(notificacao.getDestinatario());
        auditoria.setStatus(notificacao.getStatus());
        auditoria.setEvento(evento);
        auditoria.setProvedor(notificacao.getProvedor());
        auditoria.setTentativa(notificacao.getTentativas());
        auditoria.setErro(erro);
        auditoriaRepository.save(auditoria);
    }
}
