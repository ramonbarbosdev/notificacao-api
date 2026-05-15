package com.notificacao_api.service.queue;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.config.PropriedadesProtecaoNotificacao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoRequisicao;
import com.notificacao_api.dto.notificacao.EnviarNotificacaoResposta;
import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.EventoAuditoriaNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;
import com.notificacao_api.repository.NotificacaoRepository;
import com.notificacao_api.service.ContatoService;
import com.notificacao_api.service.AuditoriaNotificacaoService;
import com.notificacao_api.service.TenantContextService;

@Service
public class FilaNotificacaoService {

    private final TenantContextService tenantContextService;
    private final ContatoService contatoService;
    private final NotificacaoRepository notificacaoRepository;
    private final ProtecaoNotificacaoService protecaoService;
    private final PropriedadesProtecaoNotificacao propriedades;
    private final AuditoriaNotificacaoService auditoriaService;

    public FilaNotificacaoService(
            TenantContextService tenantContextService,
            ContatoService contatoService,
            NotificacaoRepository notificacaoRepository,
            ProtecaoNotificacaoService protecaoService,
            PropriedadesProtecaoNotificacao propriedades,
            AuditoriaNotificacaoService auditoriaService) {
        this.tenantContextService = tenantContextService;
        this.contatoService = contatoService;
        this.notificacaoRepository = notificacaoRepository;
        this.protecaoService = protecaoService;
        this.propriedades = propriedades;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public EnviarNotificacaoResposta enfileirar(EnviarNotificacaoRequisicao requisicao) {
        Long idOrganizacao = tenantContextService.idOrganizacaoObrigatoria();

        if (requisicao.canal() == CanalNotificacao.WHATSAPP) {
            contatoService.validarEnvioAutorizado(idOrganizacao, requisicao.canal(), requisicao.destinatario());
        }

        String hashDeduplicacao = protecaoService.gerarHashDeduplicacao(
                idOrganizacao,
                requisicao.canal(),
                requisicao.destinatario(),
                requisicao.mensagem());

        if (protecaoService.existeDuplicidadeRecente(
                idOrganizacao,
                requisicao.canal(),
                requisicao.destinatario(),
                hashDeduplicacao)) {
            Notificacao bloqueada = criarNotificacao(idOrganizacao, requisicao, hashDeduplicacao);
            bloqueada.setStatus(StatusNotificacao.BLOQUEADA);
            bloqueada.setErro("Mensagem duplicada bloqueada pela janela de seguranca.");
            bloqueada = notificacaoRepository.save(bloqueada);
            auditoriaService.registrar(bloqueada, EventoAuditoriaNotificacao.BLOQUEADA, bloqueada.getErro());
            return resposta(bloqueada);
        }

        Notificacao notificacao = criarNotificacao(idOrganizacao, requisicao, hashDeduplicacao);
        notificacao = notificacaoRepository.save(notificacao);
        auditoriaService.registrar(notificacao, EventoAuditoriaNotificacao.ENFILEIRADA, null);
        return resposta(notificacao);
    }

    public void validarTamanhoLote(int tamanho) {
        if (tamanho > propriedades.tamanhoMaximoLote()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Lote excede o limite operacional de " + propriedades.tamanhoMaximoLote() + " mensagens.");
        }
    }

    @Transactional
    public List<Notificacao> buscarPendentesParaProcessar() {
        return notificacaoRepository.buscarPendentesParaProcessar(
                protecaoService.agora(),
                propriedades.tamanhoLoteAgendador());
    }

    @Transactional
    public boolean marcarProcessando(Long idNotificacao) {
        int atualizadas = notificacaoRepository.marcarProcessandoSePendente(
                idNotificacao,
                protecaoService.agora());
        if (atualizadas != 1) {
            return false;
        }

        Notificacao notificacao = carregar(idNotificacao);
        auditoriaService.registrar(notificacao, EventoAuditoriaNotificacao.PROCESSANDO, null);
        return true;
    }

    @Transactional
    public Notificacao carregar(Long idNotificacao) {
        return notificacaoRepository.findById(idNotificacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificacao nao encontrada"));
    }

    @Transactional
    public void reagendar(Notificacao notificacao, LocalDateTime quando, String motivo) {
        Notificacao atual = carregar(notificacao.getIdNotificacao());
        atual.setStatus(StatusNotificacao.PENDENTE);
        atual.setErro(motivo);
        atual.setDtProximaTentativa(quando);
        notificacaoRepository.save(atual);
        auditoriaService.registrar(atual, EventoAuditoriaNotificacao.REENVIO_AGENDADO, motivo);
    }

    @Transactional
    public void marcarEnviada(Notificacao notificacao, String provedor) {
        Notificacao atual = carregar(notificacao.getIdNotificacao());
        atual.setStatus(StatusNotificacao.ENVIADA);
        atual.setProvedor(provedor);
        atual.setErro(null);
        atual.setDtEnvio(protecaoService.agora());
        notificacaoRepository.save(atual);
        auditoriaService.registrar(atual, EventoAuditoriaNotificacao.ENVIADA, null);
    }

    @Transactional
    public void marcarFalha(Notificacao notificacao, String erro) {
        marcarFalha(notificacao, erro, true);
    }

    @Transactional
    public void marcarFalha(Notificacao notificacao, String erro, boolean reenviavel) {
        Notificacao atual = carregar(notificacao.getIdNotificacao());
        int tentativas = atual.getTentativas() == null ? 1 : atual.getTentativas() + 1;
        atual.setTentativas(tentativas);
        atual.setErro(erro);

        if (!reenviavel || tentativas >= atual.getTentativasMaximas()) {
            atual.setStatus(StatusNotificacao.FALHOU);
            notificacaoRepository.save(atual);
            auditoriaService.registrar(atual, EventoAuditoriaNotificacao.FALHOU, erro);
            return;
        }

        atual.setStatus(StatusNotificacao.PENDENTE);
        atual.setDtProximaTentativa(protecaoService.calcularProximaTentativa(tentativas));
        notificacaoRepository.save(atual);
        auditoriaService.registrar(atual, EventoAuditoriaNotificacao.REENVIO_AGENDADO, erro);
    }

    private Notificacao criarNotificacao(Long idOrganizacao, EnviarNotificacaoRequisicao requisicao, String hashDeduplicacao) {
        Notificacao notificacao = new Notificacao();
        notificacao.setIdOrganizacao(idOrganizacao);
        notificacao.setCanal(requisicao.canal());
        notificacao.setDestinatario(requisicao.destinatario());
        notificacao.setAssunto(requisicao.assunto());
        notificacao.setMensagem(requisicao.mensagem());
        notificacao.setStatus(StatusNotificacao.PENDENTE);
        notificacao.setTentativasMaximas(propriedades.maximoTentativas());
        notificacao.setHashDeduplicacao(hashDeduplicacao);
        notificacao.setDtProximaTentativa(protecaoService.agora());
        return notificacao;
    }

    private EnviarNotificacaoResposta resposta(Notificacao notificacao) {
        return new EnviarNotificacaoResposta(
                notificacao.getStatus() != StatusNotificacao.BLOQUEADA,
                notificacao.getIdNotificacao(),
                notificacao.getCanal(),
                notificacao.getStatus(),
                notificacao.getErro());
    }
}
