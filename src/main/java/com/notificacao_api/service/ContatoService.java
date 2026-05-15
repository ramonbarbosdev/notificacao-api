package com.notificacao_api.service;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Contato;
import com.notificacao_api.repository.ContatoRepository;

@Service
public class ContatoService {

    private final TenantContextService tenantContextService;
    private final ContatoRepository contatoRepository;

    public ContatoService(TenantContextService tenantContextService, ContatoRepository contatoRepository) {
        this.tenantContextService = tenantContextService;
        this.contatoRepository = contatoRepository;
    }

    @Transactional
    public Contato autorizar(CanalNotificacao canal, String destinatario) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        Contato contato = contatoRepository
                .findByIdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatario)
                .orElseGet(() -> novoContato(idOrganizacao, canal, destinatario));

        contato.setConsentimento(true);
        contato.setBloqueado(false);
        contato.setMotivoBloqueio(null);
        contato.setDtConsentimento(LocalDateTime.now());
        contato.setDtBloqueio(null);
        return contatoRepository.save(contato);
    }

    @Transactional
    public Contato bloquear(CanalNotificacao canal, String destinatario, String motivo) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        Contato contato = contatoRepository
                .findByIdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatario)
                .orElseGet(() -> novoContato(idOrganizacao, canal, destinatario));

        contato.setBloqueado(true);
        contato.setMotivoBloqueio(motivo);
        contato.setDtBloqueio(LocalDateTime.now());
        return contatoRepository.save(contato);
    }

    public void validarEnvioAutorizado(Long idOrganizacao, CanalNotificacao canal, String destinatario) {
        Contato contato = contatoRepository
                .findByIdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatario)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Contato sem consentimento para o canal " + canal));

        if (!Boolean.TRUE.equals(contato.getConsentimento())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contato sem consentimento ativo.");
        }

        if (Boolean.TRUE.equals(contato.getBloqueado())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contato esta bloqueado.");
        }
    }

    private Contato novoContato(Long idOrganizacao, CanalNotificacao canal, String destinatario) {
        Contato contato = new Contato();
        contato.setIdOrganizacao(idOrganizacao);
        contato.setCanal(canal);
        contato.setDestinatario(destinatario);
        return contato;
    }
}
