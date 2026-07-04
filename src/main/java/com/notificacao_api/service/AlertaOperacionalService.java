package com.notificacao_api.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.notificacao_api.dto.alerta.AlertaOperacionalRegistrarRequest;
import com.notificacao_api.dto.alerta.AlertaOperacionalResponse;
import com.notificacao_api.model.AlertaOperacional;
import com.notificacao_api.model.ConfiguracaoGlobal;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.model.OrganizacaoConfiguracao;
import com.notificacao_api.repository.AlertaOperacionalRepository;
import com.notificacao_api.repository.ConfiguracaoGlobalRepository;
import com.notificacao_api.repository.OrganizacaoConfiguracaoRepository;

@Service
public class AlertaOperacionalService {

    public static final String ORIGEM_FILA_FALHA = "FILA_FALHA";
    public static final String ORIGEM_INTEGRACAO_EXTERNA = "INTEGRACAO_EXTERNA";

    private final AlertaOperacionalRepository repository;
    private final OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository;
    private final ConfiguracaoGlobalRepository configuracaoGlobalRepository;
    private final EmailEnvioService emailEnvioService;
    private final TenantContextService tenantContextService;

    public AlertaOperacionalService(
            AlertaOperacionalRepository repository,
            OrganizacaoConfiguracaoRepository organizacaoConfiguracaoRepository,
            ConfiguracaoGlobalRepository configuracaoGlobalRepository,
            EmailEnvioService emailEnvioService,
            TenantContextService tenantContextService) {
        this.repository = repository;
        this.organizacaoConfiguracaoRepository = organizacaoConfiguracaoRepository;
        this.configuracaoGlobalRepository = configuracaoGlobalRepository;
        this.emailEnvioService = emailEnvioService;
        this.tenantContextService = tenantContextService;
    }

    @Transactional
    public void registrarFalhaFila(Notificacao notificacao, String erro) {
        String canal = notificacao.getCanal() != null ? notificacao.getCanal().name() : null;
        String titulo = "Falha definitiva no envio de notificacao";
        String mensagem = """
                Uma notificacao falhou apos todas as tentativas.

                ID notificacao: %s
                Canal: %s
                Destinatario: %s
                Erro: %s
                """.formatted(
                valor(notificacao.getIdNotificacao()),
                valor(canal),
                valor(notificacao.getDestinatario()),
                valor(erro)).trim();

        registrarInterno(
                notificacao.getIdOrganizacao(),
                notificacao.getIdNotificacao(),
                ORIGEM_FILA_FALHA,
                titulo,
                mensagem,
                notificacao.getDestinatario(),
                canal,
                "FILA_FALHA_DEFINITIVA");
    }

    @Transactional
    public AlertaOperacionalResponse registrarIntegracaoExterna(AlertaOperacionalRegistrarRequest request) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return registrarInterno(
                idOrganizacao,
                request.idNotificacao(),
                ORIGEM_INTEGRACAO_EXTERNA,
                request.titulo(),
                request.mensagem(),
                request.destinatario(),
                request.canal(),
                request.codigoErro());
    }

    @Transactional(readOnly = true)
    public Page<AlertaOperacionalResponse> listarDaOrganizacao(Pageable pageable) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();
        return repository.findByIdOrganizacaoOrderByDtCriacaoDesc(idOrganizacao, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AlertaOperacionalResponse> listarGlobal(Pageable pageable) {
        return repository.findAllByOrderByDtCriacaoDesc(pageable).map(this::toResponse);
    }

    private AlertaOperacionalResponse registrarInterno(
            Long idOrganizacao,
            Long idNotificacao,
            String origem,
            String titulo,
            String mensagem,
            String destinatario,
            String canal,
            String codigoErro) {
        AlertaOperacional alerta = new AlertaOperacional();
        alerta.setIdOrganizacao(idOrganizacao);
        alerta.setIdNotificacao(idNotificacao);
        alerta.setTpOrigem(origem);
        alerta.setDsTitulo(titulo);
        alerta.setDsMensagem(mensagem);
        alerta.setDsDestinatario(destinatario);
        alerta.setDsCanal(canal);
        alerta.setDsCodigoErro(codigoErro);

        boolean emailEnviado = emailEnvioService.enviarAlerta(
                resolverDestinatariosEmail(idOrganizacao),
                "[Notificacao] " + titulo,
                montarCorpoEmail(idOrganizacao, titulo, mensagem, canal, destinatario, codigoErro));
        alerta.setFlEmailEnviado(emailEnviado);

        return toResponse(repository.save(alerta));
    }

    private List<String> resolverDestinatariosEmail(Long idOrganizacao) {
        List<String> destinatarios = new ArrayList<>();

        organizacaoConfiguracaoRepository.findByIdOrganizacao(idOrganizacao)
                .map(OrganizacaoConfiguracao::getDsEmailAlertas)
                .filter(StringUtils::hasText)
                .ifPresent(destinatarios::add);

        configuracaoGlobalRepository.findAll().stream()
                .findFirst()
                .map(ConfiguracaoGlobal::getNmEmailAlertas)
                .filter(StringUtils::hasText)
                .ifPresent(destinatarios::add);

        return destinatarios;
    }

    private String montarCorpoEmail(
            Long idOrganizacao,
            String titulo,
            String mensagem,
            String canal,
            String destinatario,
            String codigoErro) {
        return """
                Alerta operacional — Notificacao API

                Organizacao: %s
                Titulo: %s
                Codigo: %s
                Canal: %s
                Destinatario: %s

                Detalhes:
                %s

                ---
                Este e-mail foi gerado automaticamente. Verifique o painel de alertas para mais informacoes.
                """.formatted(
                valor(idOrganizacao),
                valor(titulo),
                valor(codigoErro),
                valor(canal),
                valor(destinatario),
                valor(mensagem)).trim();
    }

    private AlertaOperacionalResponse toResponse(AlertaOperacional alerta) {
        return new AlertaOperacionalResponse(
                alerta.getIdAlerta(),
                alerta.getIdOrganizacao(),
                alerta.getIdNotificacao(),
                alerta.getTpOrigem(),
                alerta.getDsTitulo(),
                alerta.getDsMensagem(),
                alerta.getDsDestinatario(),
                alerta.getDsCanal(),
                alerta.getDsCodigoErro(),
                alerta.isFlEmailEnviado(),
                alerta.getDtCriacao());
    }

    private String valor(Object valor) {
        return valor != null ? valor.toString() : "—";
    }
}
