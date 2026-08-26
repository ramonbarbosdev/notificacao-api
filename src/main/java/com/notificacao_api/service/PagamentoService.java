package com.notificacao_api.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.pagamento.CobrancaResponse;
import com.notificacao_api.dto.pagamento.MetodoPagamentoResponse;
import com.notificacao_api.dto.pagamento.VincularCartaoRequest;
import com.notificacao_api.enums.StatusCobranca;
import com.notificacao_api.enums.TipoMetodoPagamento;
import com.notificacao_api.integration.asaas.AsaasClient;
import com.notificacao_api.integration.asaas.dto.AsaasCustomerRequest;
import com.notificacao_api.integration.asaas.dto.AsaasCustomerResponse;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.OrganizacaoCobranca;
import com.notificacao_api.model.OrganizacaoMetodoPagamento;
import com.notificacao_api.model.Usuario;
import com.notificacao_api.repository.OrganizacaoCobrancaRepository;
import com.notificacao_api.repository.OrganizacaoMetodoPagamentoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.UsuarioRepository;

@Service
public class PagamentoService {

    private final TenantContextService tenantContextService;
    private final OrganizacaoRepository organizacaoRepository;
    private final OrganizacaoMetodoPagamentoRepository metodoPagamentoRepository;
    private final OrganizacaoCobrancaRepository cobrancaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsaasClient asaasClient;

    public PagamentoService(
            TenantContextService tenantContextService,
            OrganizacaoRepository organizacaoRepository,
            OrganizacaoMetodoPagamentoRepository metodoPagamentoRepository,
            OrganizacaoCobrancaRepository cobrancaRepository,
            UsuarioRepository usuarioRepository,
            AsaasClient asaasClient) {
        this.tenantContextService = tenantContextService;
        this.organizacaoRepository = organizacaoRepository;
        this.metodoPagamentoRepository = metodoPagamentoRepository;
        this.cobrancaRepository = cobrancaRepository;
        this.usuarioRepository = usuarioRepository;
        this.asaasClient = asaasClient;
    }

    @Transactional(readOnly = true)
    public List<MetodoPagamentoResponse> listarMetodos() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return metodoPagamentoRepository.findByIdOrganizacaoAndFlAtivoTrueOrderByFlPadraoDescDtCriacaoDesc(idOrganizacao)
                .stream()
                .map(this::toMetodoResponse)
                .toList();
    }

    @Transactional
    public MetodoPagamentoResponse vincularCartao(VincularCartaoRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        garantirClienteAsaas(carregarOrganizacao(idOrganizacao));

        boolean padrao = request.padrao() == null || request.padrao();
        if (padrao) {
            metodoPagamentoRepository.findByIdOrganizacaoAndFlPadraoTrueAndFlAtivoTrue(idOrganizacao)
                    .ifPresent(existente -> {
                        existente.setFlPadrao(false);
                        metodoPagamentoRepository.save(existente);
                    });
        }

        OrganizacaoMetodoPagamento metodo = new OrganizacaoMetodoPagamento();
        metodo.setIdOrganizacao(idOrganizacao);
        metodo.setTipo(TipoMetodoPagamento.CARTAO);
        metodo.setIdCartaoAsaas(request.creditCardToken());
        metodo.setNuUltimos4(request.ultimos4Digitos());
        metodo.setDsBandeira(request.bandeira());
        metodo.setFlPadrao(padrao);
        return toMetodoResponse(metodoPagamentoRepository.save(metodo));
    }

    @Transactional
    public void removerCartao(Long idMetodoPagamento) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoMetodoPagamento metodo = metodoPagamentoRepository
                .findByIdMetodoPagamentoAndIdOrganizacaoAndFlAtivoTrue(idMetodoPagamento, idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metodo de pagamento nao encontrado."));
        metodo.setFlAtivo(false);
        metodo.setFlPadrao(false);
        metodoPagamentoRepository.save(metodo);
    }

    @Transactional
    public MetodoPagamentoResponse definirCartaoPadrao(Long idMetodoPagamento) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoMetodoPagamento metodo = metodoPagamentoRepository
                .findByIdMetodoPagamentoAndIdOrganizacaoAndFlAtivoTrue(idMetodoPagamento, idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Metodo de pagamento nao encontrado."));

        metodoPagamentoRepository.findByIdOrganizacaoAndFlPadraoTrueAndFlAtivoTrue(idOrganizacao)
                .ifPresent(existente -> {
                    existente.setFlPadrao(false);
                    metodoPagamentoRepository.save(existente);
                });

        metodo.setFlPadrao(true);
        return toMetodoResponse(metodoPagamentoRepository.save(metodo));
    }

    @Transactional(readOnly = true)
    public List<CobrancaResponse> listarCobrancas() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return cobrancaRepository.findByIdOrganizacaoOrderByDtCriacaoDesc(idOrganizacao).stream()
                .map(this::toCobrancaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CobrancaResponse> listarCobrancasPendentes() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return cobrancaRepository.findByIdOrganizacaoAndStatusOrderByDtCriacaoDesc(idOrganizacao, StatusCobranca.PENDENTE)
                .stream()
                .map(this::toCobrancaResponse)
                .toList();
    }

    private String garantirClienteAsaas(Organizacao organizacao) {
        if (organizacao.getIdClienteAsaas() != null && !organizacao.getIdClienteAsaas().isBlank()) {
            return organizacao.getIdClienteAsaas();
        }
        Usuario usuario = usuarioRepository.findById(tenantContextService.atual().getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
        String documento = organizacao.getDsDocumento() != null
                ? organizacao.getDsDocumento().replaceAll("\\D", "")
                : "00000000000191";
        String email = usuario.getNmEmail() != null
                ? usuario.getNmEmail()
                : "org" + organizacao.getIdOrganizacao() + "@notificacao.local";
        AsaasCustomerResponse cliente = asaasClient.criarCliente(new AsaasCustomerRequest(
                organizacao.getNmOrganizacao(),
                documento,
                email,
                String.valueOf(organizacao.getIdOrganizacao())));
        organizacao.setIdClienteAsaas(cliente.id());
        organizacaoRepository.save(organizacao);
        return cliente.id();
    }

    private Organizacao carregarOrganizacao(Long idOrganizacao) {
        return organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada."));
    }

    private MetodoPagamentoResponse toMetodoResponse(OrganizacaoMetodoPagamento metodo) {
        return new MetodoPagamentoResponse(
                metodo.getIdMetodoPagamento(),
                metodo.getTipo(),
                metodo.getNuUltimos4(),
                metodo.getDsBandeira(),
                metodo.getFlPadrao());
    }

    private CobrancaResponse toCobrancaResponse(OrganizacaoCobranca cobranca) {
        return new CobrancaResponse(
                cobranca.getIdOrganizacaoCobranca(),
                cobranca.getIdCobrancaAsaas(),
                cobranca.getVlCobranca(),
                cobranca.getStatus(),
                cobranca.getDsPixCopiaCola(),
                cobranca.getDsPixQrBase64(),
                cobranca.getDtVencimento(),
                cobranca.getDtPagamento(),
                cobranca.getDtCriacao());
    }
}
