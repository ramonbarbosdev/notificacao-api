package com.notificacao_api.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.contato.ContatoFilter;
import com.notificacao_api.dto.contato.ContatoResponseDTO;
import com.notificacao_api.dto.contato.SincronizarContatosWhatsappResponseDTO;
import com.notificacao_api.dto.whatsapp.WhatsappContatoGatewayItemDTO;
import com.notificacao_api.dto.whatsapp.WhatsappContatosGatewayResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.model.Contato;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.repository.ContatoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.service.whatsapp.WhatsAppGatewayClient;
import com.notificacao_api.shared.GenericSpecificationBuilder;
import com.notificacao_api.shared.TelefoneBrasilUtil;

@Service
public class ContatoService {

    private final ContatoRepository contatoRepository;
    private final TenantContextService tenantContextService;
    private final OrganizacaoRepository organizacaoRepository;
    private final WhatsAppGatewayClient gatewayClient;

    public ContatoService(
            TenantContextService tenantContextService,
            ContatoRepository contatoRepository,
            OrganizacaoRepository organizacaoRepository,
            WhatsAppGatewayClient gatewayClient) {
        this.contatoRepository = contatoRepository;
        this.tenantContextService = tenantContextService;
        this.organizacaoRepository = organizacaoRepository;
        this.gatewayClient = gatewayClient;
    }

    @Transactional
    public Contato autorizar(CanalNotificacao canal, String destinatario, String nmContato) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return autorizarOrganizacao(idOrganizacao, canal, destinatario, nmContato);
    }

    @Transactional
    public Contato autorizarOrganizacao(
            Long idOrganizacao,
            String destinatario,
            String nmContato) {
        return autorizarOrganizacao(idOrganizacao, CanalNotificacao.WHATSAPP, destinatario, nmContato);
    }

    @Transactional
    public Contato autorizarOrganizacao(
            Long idOrganizacao,
            CanalNotificacao canal,
            String destinatario,
            String nmContato) {
        String destinatarioNormalizado = normalizarDestinatario(canal, destinatario);
        Contato contato = contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatarioNormalizado)
                .orElseGet(() -> novoContato(idOrganizacao, canal, destinatarioNormalizado, nmContato));

        contato.setNmContato(nmContato);
        contato.setConsentimento(true);
        contato.setBloqueado(false);
        contato.setMotivoBloqueio(null);
        contato.setDtConsentimento(LocalDateTime.now());
        contato.setDtBloqueio(null);
        contato.setSincronizadoWhatsapp(false);
        contato.setDtAtualizacao(LocalDateTime.now());
        return contatoRepository.save(contato);
    }

    @Transactional
    public Contato registrarInboundPendente(Long idOrganizacao, String telefone, String nmContato) {
        String destinatario = normalizarDestinatario(CanalNotificacao.WHATSAPP, telefone);
        String nomeValido = TelefoneBrasilUtil.resolverNomeContatoWhatsapp(nmContato, destinatario);
        Contato contato = contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                        idOrganizacao,
                        CanalNotificacao.WHATSAPP,
                        destinatario)
                .orElseGet(() -> novoContato(
                        idOrganizacao,
                        CanalNotificacao.WHATSAPP,
                        destinatario,
                        nomeValido != null ? nomeValido : destinatario));

        if (nomeValido != null && deveAtualizarNomeInbound(contato, destinatario, nomeValido)) {
            contato.setNmContato(nomeValido);
        }

        if (contato.getConsentimento() == null) {
            contato.setConsentimento(false);
        }
        if (contato.getBloqueado() == null) {
            contato.setBloqueado(false);
        }

        contato.setDtAtualizacao(LocalDateTime.now());
        return contatoRepository.save(contato);
    }

    @Transactional
    public Contato bloquear(CanalNotificacao canal, String destinatario, String nmContato, String motivo) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        String destinatarioNormalizado = normalizarDestinatario(canal, destinatario);
        Contato contato = contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatarioNormalizado)
                .orElseGet(() -> novoContato(idOrganizacao, canal, destinatarioNormalizado, nmContato));

        contato.setBloqueado(true);
        contato.setMotivoBloqueio(motivo);
        contato.setDtBloqueio(LocalDateTime.now());
        contato.setSincronizadoWhatsapp(false);
        return contatoRepository.save(contato);
    }

    public void validarEnvioAutorizado(Long idOrganizacao, CanalNotificacao canal, String destinatario) {
        String destinatarioNormalizado = normalizarDestinatario(canal, destinatario);
        Contato contato = contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatarioNormalizado)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Contato sem consentimento para o canal " + canal));

        if (!Boolean.TRUE.equals(contato.getConsentimento())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contato sem consentimento ativo.");
        }

        validarNaoBloqueado(contato);
    }

    public void validarNaoBloqueado(Long idOrganizacao, CanalNotificacao canal, String destinatario) {
        String destinatarioNormalizado = normalizarDestinatario(canal, destinatario);
        contatoRepository
                .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(idOrganizacao, canal, destinatarioNormalizado)
                .ifPresent(this::validarNaoBloqueado);
    }

    private void validarNaoBloqueado(Contato contato) {
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
    public void excluir(Long idContato) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        Contato contato = contatoRepository.findById(idContato)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato nao encontrado."));

        if (!idOrganizacao.equals(contato.getOrganizacao().getIdOrganizacao())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato nao encontrado.");
        }

        contatoRepository.delete(contato);
    }

    @Transactional
    public SincronizarContatosWhatsappResponseDTO sincronizarWhatsapp() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return sincronizarWhatsappOrganizacao(idOrganizacao);
    }

    @Transactional
    public SincronizarContatosWhatsappResponseDTO sincronizarWhatsappOrganizacao(Long idOrganizacao) {
        WhatsappContatosGatewayResposta respostaGateway = gatewayClient.listarContatos(idOrganizacao);

        if (!Boolean.TRUE.equals(respostaGateway.sucesso())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    respostaGateway.erro() != null && !respostaGateway.erro().isBlank()
                            ? respostaGateway.erro()
                            : "Nao foi possivel listar contatos no gateway WhatsApp.");
        }

        List<WhatsappContatoGatewayItemDTO> contatosGateway = respostaGateway.contatos() == null
                ? List.of()
                : respostaGateway.contatos();

        int importados = 0;
        int atualizados = 0;
        Set<String> destinatariosGateway = new HashSet<>();

        for (WhatsappContatoGatewayItemDTO item : contatosGateway) {
            if (item == null || !StringUtils.hasText(item.telefone())) {
                continue;
            }

            String destinatario = normalizarDestinatario(CanalNotificacao.WHATSAPP, item.telefone());
            if (!destinatarioWhatsappImportavel(destinatario)) {
                continue;
            }

            destinatariosGateway.add(destinatario);
            String nmContato = resolverNomeContato(item, destinatario);

            Optional<Contato> existente = contatoRepository
                    .findByOrganizacao_IdOrganizacaoAndCanalAndDestinatario(
                            idOrganizacao,
                            CanalNotificacao.WHATSAPP,
                            destinatario);

            if (existente.isPresent()) {
                Contato contato = existente.get();
                boolean alterou = false;

                if (StringUtils.hasText(nmContato) && !nmContato.equals(contato.getNmContato())) {
                    if (!Boolean.TRUE.equals(contato.getConsentimento())) {
                        contato.setNmContato(nmContato);
                        alterou = true;
                    }
                }

                if (!Boolean.TRUE.equals(contato.getConsentimento())
                        && !Boolean.TRUE.equals(contato.getBloqueado())
                        && !Boolean.TRUE.equals(contato.getSincronizadoWhatsapp())) {
                    contato.setSincronizadoWhatsapp(true);
                    alterou = true;
                }

                if (alterou) {
                    contato.setDtAtualizacao(LocalDateTime.now());
                    contatoRepository.save(contato);
                    atualizados++;
                }
                continue;
            }

            Contato novo = novoContato(idOrganizacao, CanalNotificacao.WHATSAPP, destinatario, nmContato);
            novo.setSincronizadoWhatsapp(true);
            novo.setConsentimento(false);
            novo.setBloqueado(false);
            contatoRepository.save(novo);
            importados++;
        }

        int removidos = removerContatosSincronizadosAusentes(idOrganizacao, destinatariosGateway);
        int totalGateway = respostaGateway.total() != null
                ? respostaGateway.total()
                : contatosGateway.size();

        return new SincronizarContatosWhatsappResponseDTO(importados, atualizados, removidos, totalGateway);
    }

    @Transactional
    public int limparContatosSincronizadosWhatsapp(Long idOrganizacao) {
        List<Contato> sincronizados = contatoRepository.findByOrganizacao_IdOrganizacaoAndCanalAndSincronizadoWhatsapp(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                true);

        int removidos = 0;
        for (Contato contato : sincronizados) {
            if (Boolean.TRUE.equals(contato.getConsentimento()) || Boolean.TRUE.equals(contato.getBloqueado())) {
                contato.setSincronizadoWhatsapp(false);
                contato.setDtAtualizacao(LocalDateTime.now());
                contatoRepository.save(contato);
                continue;
            }

            contatoRepository.delete(contato);
            removidos++;
        }

        return removidos;
    }

    private int removerContatosSincronizadosAusentes(Long idOrganizacao, Set<String> destinatariosGateway) {
        List<Contato> sincronizados = contatoRepository.findByOrganizacao_IdOrganizacaoAndCanalAndSincronizadoWhatsapp(
                idOrganizacao,
                CanalNotificacao.WHATSAPP,
                true);

        int removidos = 0;
        for (Contato contato : sincronizados) {
            if (destinatariosGateway.contains(contato.getDestinatario())) {
                continue;
            }

            if (Boolean.TRUE.equals(contato.getConsentimento()) || Boolean.TRUE.equals(contato.getBloqueado())) {
                contato.setSincronizadoWhatsapp(false);
                contato.setDtAtualizacao(LocalDateTime.now());
                contatoRepository.save(contato);
                continue;
            }

            contatoRepository.delete(contato);
            removidos++;
        }

        return removidos;
    }

    private String resolverNomeContato(WhatsappContatoGatewayItemDTO item, String destinatario) {
        if (item != null && StringUtils.hasText(item.nmContato()) && !destinatario.equals(item.nmContato())) {
            return item.nmContato().trim();
        }

        return destinatario;
    }

    private boolean destinatarioWhatsappImportavel(String destinatario) {
        if (!StringUtils.hasText(destinatario)) {
            return false;
        }

        String digitos = destinatario.replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 13) {
            return false;
        }

        return digitos.startsWith("55") && TelefoneBrasilUtil.celularBrasilComNonoDigito(digitos);
    }

    private String normalizarDestinatario(CanalNotificacao canal, String destinatario) {
        return TelefoneBrasilUtil.normalizarDestino(canal, destinatario);
    }

    private boolean deveAtualizarNomeInbound(Contato contato, String destinatario, String nomeNovo) {
        if (!StringUtils.hasText(contato.getNmContato())) {
            return true;
        }

        if (contato.getNmContato().equals(destinatario)) {
            return true;
        }

        if (TelefoneBrasilUtil.nomePareceTelefone(contato.getNmContato(), destinatario)) {
            return true;
        }

        if (Boolean.TRUE.equals(contato.getConsentimento())) {
            return false;
        }

        return nomeNovo.equalsIgnoreCase(contato.getNmContato().trim());
    }

    private Contato novoContato(Long idOrganizacao, CanalNotificacao canal, String destinatario, String nmContato) {
        Organizacao organizacao = organizacaoRepository.getReferenceById(idOrganizacao);

        Contato contato = new Contato();
        contato.setNmContato(nmContato);
        contato.setOrganizacao(organizacao);
        contato.setCanal(canal);
        contato.setDestinatario(destinatario);
        return contato;
    }

    public ContatoResponseDTO toResponse(Contato contato) {
        return new ContatoResponseDTO(
                contato.getIdContato(),
                contato.getCanal(),
                contato.getNmContato(),
                contato.getDestinatario(),
                contato.getConsentimento(),
                contato.getBloqueado(),
                contato.getMotivoBloqueio(),
                contato.getDtConsentimento(),
                contato.getDtBloqueio());
    }
}
