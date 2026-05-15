package com.notificacao_api.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.contato.ContatoFilter;
import com.notificacao_api.dto.contato.ContatoResponseDTO;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Contato;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.repository.ContatoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.shared.GenericSpecificationBuilder;

@Service
public class ContatoService {

    private final TenantContextService tenantContextService;
    private final ContatoRepository contatoRepository;
    private final OrganizacaoRepository organizacaoRepository;

    public ContatoService(TenantContextService tenantContextService, ContatoRepository contatoRepository,OrganizacaoRepository organizacaoRepository) {
        this.tenantContextService = tenantContextService;
        this.contatoRepository = contatoRepository;
           this.organizacaoRepository = organizacaoRepository;
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

    @Transactional(readOnly = true)
    public Page<ContatoResponseDTO> listar(ContatoFilter filter, Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        Specification<Contato> tenantSpec = (root, query, cb) -> cb.equal(
                root.get("organizacao").get("idOrganizacao"),
                idOrganizacao);

        Specification<Contato> filterSpec = GenericSpecificationBuilder.byFilter(filter);

        return contatoRepository.findAll(tenantSpec.and(filterSpec), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void sincronizarWhatsapp() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        // Por enquanto mock/placeholder.
        // Depois aqui você chama o gateway: Evolution API, Baileys, WPPConnect etc.
        //
        // Fluxo futuro:
        // 1. Buscar contatos/chats no gateway
        // 2. Normalizar número
        // 3. Salvar contato se não existir
        // 4. Nunca marcar consentimento automaticamente
    }

    private Contato novoContato(Long idOrganizacao, CanalNotificacao canal, String destinatario) {
        Organizacao organizacao = organizacaoRepository.getReferenceById(idOrganizacao);

        Contato contato = new Contato();
        contato.setOrganizacao(organizacao);
        contato.setCanal(canal);
        contato.setDestinatario(destinatario);
        return contato;
    }

    public ContatoResponseDTO toResponse(Contato contato) {
        return new ContatoResponseDTO(
                contato.getIdContato(),
                contato.getCanal(),
                contato.getDestinatario(),
                contato.getConsentimento(),
                contato.getBloqueado(),
                contato.getMotivoBloqueio(),
                contato.getDtConsentimento(),
                contato.getDtBloqueio());
    }
}
