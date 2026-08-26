package com.notificacao_api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.pagamento.AssinaturaResponse;
import com.notificacao_api.dto.pagamento.ContratarAssinaturaRequest;
import com.notificacao_api.dto.pagamento.PlanoDisponivelResponse;
import com.notificacao_api.enums.FormaPagamentoAssinatura;
import com.notificacao_api.enums.StatusAssinatura;
import com.notificacao_api.integration.asaas.AsaasClient;
import com.notificacao_api.integration.asaas.dto.AsaasCustomerRequest;
import com.notificacao_api.integration.asaas.dto.AsaasCustomerResponse;
import com.notificacao_api.integration.asaas.dto.AsaasPaymentListResponse;
import com.notificacao_api.integration.asaas.dto.AsaasPaymentResponse;
import com.notificacao_api.integration.asaas.dto.AsaasSubscriptionRequest;
import com.notificacao_api.integration.asaas.dto.AsaasSubscriptionResponse;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.OrganizacaoAssinatura;
import com.notificacao_api.model.OrganizacaoMetodoPagamento;
import com.notificacao_api.model.Plano;
import com.notificacao_api.model.Usuario;
import com.notificacao_api.repository.OrganizacaoAssinaturaRepository;
import com.notificacao_api.repository.OrganizacaoMetodoPagamentoRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.PlanoRepository;
import com.notificacao_api.repository.UsuarioRepository;

@Service
public class AssinaturaService {

    private static final DateTimeFormatter ASAAS_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TenantContextService tenantContextService;
    private final OrganizacaoRepository organizacaoRepository;
    private final OrganizacaoAssinaturaRepository assinaturaRepository;
    private final OrganizacaoMetodoPagamentoRepository metodoPagamentoRepository;
    private final PlanoRepository planoRepository;
    private final AsaasClient asaasClient;
    private final OrganizacaoCobrancaService cobrancaService;
    private final UsuarioRepository usuarioRepository;

    public AssinaturaService(
            TenantContextService tenantContextService,
            OrganizacaoRepository organizacaoRepository,
            OrganizacaoAssinaturaRepository assinaturaRepository,
            OrganizacaoMetodoPagamentoRepository metodoPagamentoRepository,
            PlanoRepository planoRepository,
            AsaasClient asaasClient,
            OrganizacaoCobrancaService cobrancaService,
            UsuarioRepository usuarioRepository) {
        this.tenantContextService = tenantContextService;
        this.organizacaoRepository = organizacaoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.metodoPagamentoRepository = metodoPagamentoRepository;
        this.planoRepository = planoRepository;
        this.asaasClient = asaasClient;
        this.cobrancaService = cobrancaService;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public java.util.List<PlanoDisponivelResponse> listarPlanosDisponiveis() {
        return planoRepository.findAllByOrderByNmPlanoAsc().stream()
                .filter(Plano::getFlAtivo)
                .map(this::toPlanoDisponivel)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssinaturaResponse buscarAtual() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        Organizacao organizacao = carregarOrganizacao(idOrganizacao);
        Plano plano = carregarPlano(organizacao.getIdPlano());
        return assinaturaRepository.findByIdOrganizacao(idOrganizacao)
                .map(assinatura -> toResponse(assinatura, plano))
                .orElseGet(() -> assinaturaImplicita(idOrganizacao, plano));
    }

    @Transactional
    public AssinaturaResponse contratar(ContratarAssinaturaRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        Organizacao organizacao = carregarOrganizacao(idOrganizacao);
        Plano plano = carregarPlano(request.idPlano());

        if (!Boolean.TRUE.equals(plano.getFlAtivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plano selecionado esta inativo.");
        }

        OrganizacaoAssinatura assinatura = assinaturaRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> novaAssinatura(idOrganizacao, plano.getIdPlano()));

        if (planoGratuito(plano)) {
            return ativarPlanoGratuito(organizacao, assinatura, plano);
        }

        if (plano.getNuDiasTrial() != null
                && plano.getNuDiasTrial() > 0
                && assinatura.getStatus() == null) {
            return iniciarTrial(organizacao, assinatura, plano);
        }

        cancelarAssinaturaAsaasSeExistir(assinatura);

        String idCliente = garantirClienteAsaas(organizacao);
        String billingType = request.formaPagamento() == FormaPagamentoAssinatura.PIX ? "PIX" : "CREDIT_CARD";
        String creditCardToken = null;
        if (request.formaPagamento() == FormaPagamentoAssinatura.CARTAO) {
            creditCardToken = metodoPagamentoRepository
                    .findByIdOrganizacaoAndFlPadraoTrueAndFlAtivoTrue(idOrganizacao)
                    .map(OrganizacaoMetodoPagamento::getIdCartaoAsaas)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Vincule um cartao padrao antes de assinar com cartao."));
        }

        LocalDate proximoVencimento = LocalDate.now().plusDays(1);
        AsaasSubscriptionResponse respostaAsaas = asaasClient.criarAssinatura(new AsaasSubscriptionRequest(
                idCliente,
                billingType,
                plano.getVlMensal(),
                proximoVencimento.format(ASAAS_DATE),
                "MONTHLY",
                creditCardToken,
                "Assinatura " + plano.getNmPlano()));

        assinatura.setIdPlano(plano.getIdPlano());
        assinatura.setFormaPagamento(request.formaPagamento());
        assinatura.setIdAssinaturaAsaas(respostaAsaas.id());
        assinatura.setDtProximoVencimento(parseDate(respostaAsaas.nextDueDate(), proximoVencimento));
        assinatura.setStatus(StatusAssinatura.PENDENTE);

        organizacao.setIdPlano(plano.getIdPlano());
        organizacaoRepository.save(organizacao);
        assinaturaRepository.save(assinatura);

        if (request.formaPagamento() == FormaPagamentoAssinatura.PIX) {
            sincronizarCobrancaPixPendente(idOrganizacao, respostaAsaas.id());
        }

        return toResponse(assinatura, plano);
    }

    @Transactional
    public AssinaturaResponse cancelar() {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        OrganizacaoAssinatura assinatura = assinaturaRepository.findByIdOrganizacao(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura nao encontrada."));
        cancelarAssinaturaAsaasSeExistir(assinatura);
        assinatura.setStatus(StatusAssinatura.CANCELADA);
        assinatura.setIdAssinaturaAsaas(null);
        assinaturaRepository.save(assinatura);
        Plano plano = carregarPlano(assinatura.getIdPlano());
        return toResponse(assinatura, plano);
    }

    @Transactional
    public void ativarPorPagamento(Long idOrganizacao, Long idPlano) {
        Organizacao organizacao = carregarOrganizacao(idOrganizacao);
        Plano plano = carregarPlano(idPlano);
        OrganizacaoAssinatura assinatura = assinaturaRepository.findByIdOrganizacao(idOrganizacao)
                .orElseGet(() -> novaAssinatura(idOrganizacao, idPlano));
        assinatura.setIdPlano(idPlano);
        assinatura.setStatus(StatusAssinatura.ATIVA);
        assinatura.setDtFimTrial(null);
        organizacao.setIdPlano(idPlano);
        organizacaoRepository.save(organizacao);
        assinaturaRepository.save(assinatura);
    }

    @Transactional
    public void marcarInadimplente(Long idOrganizacao) {
        assinaturaRepository.findByIdOrganizacao(idOrganizacao).ifPresent(assinatura -> {
            assinatura.setStatus(StatusAssinatura.INADIMPLENTE);
            assinaturaRepository.save(assinatura);
        });
    }

    private AssinaturaResponse ativarPlanoGratuito(
            Organizacao organizacao,
            OrganizacaoAssinatura assinatura,
            Plano plano) {
        assinatura.setIdPlano(plano.getIdPlano());
        assinatura.setStatus(StatusAssinatura.ATIVA);
        assinatura.setFormaPagamento(null);
        assinatura.setIdAssinaturaAsaas(null);
        assinatura.setDtFimTrial(null);
        organizacao.setIdPlano(plano.getIdPlano());
        organizacaoRepository.save(organizacao);
        assinaturaRepository.save(assinatura);
        return toResponse(assinatura, plano);
    }

    private AssinaturaResponse iniciarTrial(
            Organizacao organizacao,
            OrganizacaoAssinatura assinatura,
            Plano plano) {
        assinatura.setIdPlano(plano.getIdPlano());
        assinatura.setStatus(StatusAssinatura.TRIAL);
        assinatura.setDtFimTrial(LocalDateTime.now().plusDays(plano.getNuDiasTrial()));
        organizacao.setIdPlano(plano.getIdPlano());
        organizacaoRepository.save(organizacao);
        assinaturaRepository.save(assinatura);
        return toResponse(assinatura, plano);
    }

    private void cancelarAssinaturaAsaasSeExistir(OrganizacaoAssinatura assinatura) {
        if (StringUtils.hasText(assinatura.getIdAssinaturaAsaas())) {
            try {
                asaasClient.cancelarAssinatura(assinatura.getIdAssinaturaAsaas());
            } catch (ResponseStatusException ignored) {
                // assinatura pode ja estar cancelada no gateway
            }
        }
    }

    private void sincronizarCobrancaPixPendente(Long idOrganizacao, String idAssinaturaAsaas) {
        AsaasPaymentListResponse lista = asaasClient.listarPagamentosAssinatura(idAssinaturaAsaas);
        if (lista == null || lista.data() == null) {
            return;
        }
        lista.data().stream()
                .filter(p -> "PENDING".equalsIgnoreCase(p.status()) || "OVERDUE".equalsIgnoreCase(p.status()))
                .findFirst()
                .ifPresent(pagamento -> cobrancaService.registrarOuAtualizar(idOrganizacao, pagamento));
    }

    private String garantirClienteAsaas(Organizacao organizacao) {
        if (StringUtils.hasText(organizacao.getIdClienteAsaas())) {
            return organizacao.getIdClienteAsaas();
        }
        Usuario usuario = usuarioRepository.findById(tenantContextService.atual().getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
        String documento = StringUtils.hasText(organizacao.getDsDocumento())
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

    private OrganizacaoAssinatura novaAssinatura(Long idOrganizacao, Long idPlano) {
        OrganizacaoAssinatura assinatura = new OrganizacaoAssinatura();
        assinatura.setIdOrganizacao(idOrganizacao);
        assinatura.setIdPlano(idPlano);
        return assinatura;
    }

    private AssinaturaResponse assinaturaImplicita(Long idOrganizacao, Plano plano) {
        return new AssinaturaResponse(
                null,
                plano.getIdPlano(),
                plano.getNmPlano(),
                planoGratuito(plano) ? StatusAssinatura.ATIVA : StatusAssinatura.PENDENTE,
                null,
                plano.getVlMensal(),
                null,
                null);
    }

    private AssinaturaResponse toResponse(OrganizacaoAssinatura assinatura, Plano plano) {
        return new AssinaturaResponse(
                assinatura.getIdOrganizacaoAssinatura(),
                assinatura.getIdPlano(),
                plano.getNmPlano(),
                assinatura.getStatus(),
                assinatura.getFormaPagamento(),
                plano.getVlMensal(),
                assinatura.getDtProximoVencimento(),
                assinatura.getDtFimTrial());
    }

    private PlanoDisponivelResponse toPlanoDisponivel(Plano plano) {
        return new PlanoDisponivelResponse(
                plano.getIdPlano(),
                plano.getNmPlano(),
                plano.getDsPlano(),
                plano.getVlMensal(),
                plano.getNuDiasTrial(),
                plano.getNuLimiteMensagensMensal(),
                plano.getNuLimiteUsuarios(),
                plano.getNuLimiteTemplates(),
                plano.getFlWhatsappHabilitado(),
                plano.getFlEmailHabilitado(),
                plano.getFlTelegramHabilitado(),
                plano.getFlWebhookHabilitado(),
                plano.getFlApiPublicaHabilitada());
    }

    private Organizacao carregarOrganizacao(Long idOrganizacao) {
        return organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada."));
    }

    private Plano carregarPlano(Long idPlano) {
        if (idPlano == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organizacao sem plano vinculado.");
        }
        return planoRepository.findById(idPlano)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano nao encontrado."));
    }

    private boolean planoGratuito(Plano plano) {
        return plano.getVlMensal() == null || plano.getVlMensal().compareTo(BigDecimal.ZERO) <= 0;
    }

    private LocalDate parseDate(String valor, LocalDate fallback) {
        if (!StringUtils.hasText(valor)) {
            return fallback;
        }
        return LocalDate.parse(valor, ASAAS_DATE);
    }
}
