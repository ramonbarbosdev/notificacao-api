package com.notificacao_api.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.dto.configuracao.PlanoRequest;
import com.notificacao_api.dto.configuracao.PlanoResponse;
import com.notificacao_api.model.Plano;
import com.notificacao_api.repository.PlanoRepository;

@Service
public class PlanoService {

    private final PlanoRepository planoRepository;
    private final AuditoriaEventoService auditoriaService;

    public PlanoService(PlanoRepository planoRepository, AuditoriaEventoService auditoriaService) {
        this.planoRepository = planoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<PlanoResponse> listar() {
        return planoRepository.findAllByOrderByNmPlanoAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanoResponse buscar(Long idPlano) {
        return toResponse(carregar(idPlano));
    }

    @Transactional
    public PlanoResponse criar(PlanoRequest request) {
        Plano plano = new Plano();
        aplicar(plano, request);
        Plano salvo = planoRepository.save(plano);
        auditoriaService.registrar(null, "PLANO", "CRIAR", "Plano criado.", null, toResponse(salvo));
        return toResponse(salvo);
    }

    @Transactional
    public PlanoResponse atualizar(Long idPlano, PlanoRequest request) {
        Plano plano = carregar(idPlano);
        PlanoResponse antes = toResponse(plano);
        aplicar(plano, request);
        Plano salvo = planoRepository.save(plano);
        PlanoResponse depois = toResponse(salvo);
        auditoriaService.registrar(null, "PLANO", "ATUALIZAR", "Plano atualizado.", antes, depois);
        return depois;
    }

    @Transactional
    public PlanoResponse alterarStatus(Long idPlano, boolean ativo) {
        Plano plano = carregar(idPlano);
        PlanoResponse antes = toResponse(plano);
        plano.setFlAtivo(ativo);
        Plano salvo = planoRepository.save(plano);
        PlanoResponse depois = toResponse(salvo);
        auditoriaService.registrar(null, "PLANO", ativo ? "ATIVAR" : "INATIVAR", "Status do plano alterado.", antes, depois);
        return depois;
    }

    private Plano carregar(Long idPlano) {
        return planoRepository.findById(idPlano)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano nao encontrado."));
    }

    private void aplicar(Plano plano, PlanoRequest request) {
        plano.setNmPlano(request.nmPlano());
        plano.setDsPlano(request.dsPlano());
        plano.setNuLimiteMensagensMensal(request.nuLimiteMensagensMensal());
        plano.setNuLimiteUsuarios(request.nuLimiteUsuarios());
        plano.setNuLimiteTemplates(request.nuLimiteTemplates());
        plano.setNuLimiteContatos(request.nuLimiteContatos());
        plano.setFlWhatsappHabilitado(request.flWhatsappHabilitado() == null || request.flWhatsappHabilitado());
        plano.setFlEmailHabilitado(Boolean.TRUE.equals(request.flEmailHabilitado()));
        plano.setFlTelegramHabilitado(Boolean.TRUE.equals(request.flTelegramHabilitado()));
        plano.setFlWebhookHabilitado(request.flWebhookHabilitado() == null || request.flWebhookHabilitado());
        plano.setFlApiPublicaHabilitada(Boolean.TRUE.equals(request.flApiPublicaHabilitada()));
        plano.setFlAtivo(request.flAtivo() == null || request.flAtivo());
    }

    public PlanoResponse toResponse(Plano plano) {
        return new PlanoResponse(
                plano.getIdPlano(),
                plano.getNmPlano(),
                plano.getDsPlano(),
                plano.getNuLimiteMensagensMensal(),
                plano.getNuLimiteUsuarios(),
                plano.getNuLimiteTemplates(),
                plano.getNuLimiteContatos(),
                plano.getFlWhatsappHabilitado(),
                plano.getFlEmailHabilitado(),
                plano.getFlTelegramHabilitado(),
                plano.getFlWebhookHabilitado(),
                plano.getFlApiPublicaHabilitada(),
                plano.getFlAtivo(),
                plano.getDtCriacao(),
                plano.getDtAtualizacao());
    }
}
