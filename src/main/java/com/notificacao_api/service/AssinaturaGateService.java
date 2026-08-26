package com.notificacao_api.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.enums.StatusAssinatura;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.OrganizacaoAssinatura;
import com.notificacao_api.model.Plano;
import com.notificacao_api.repository.OrganizacaoAssinaturaRepository;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.PlanoRepository;

@Service
public class AssinaturaGateService {

    private final OrganizacaoRepository organizacaoRepository;
    private final OrganizacaoAssinaturaRepository assinaturaRepository;
    private final PlanoRepository planoRepository;

    public AssinaturaGateService(
            OrganizacaoRepository organizacaoRepository,
            OrganizacaoAssinaturaRepository assinaturaRepository,
            PlanoRepository planoRepository) {
        this.organizacaoRepository = organizacaoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.planoRepository = planoRepository;
    }

    @Transactional(readOnly = true)
    public void validarOrganizacaoAtiva(Long idOrganizacao) {
        if (podeUtilizarPlataforma(idOrganizacao)) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.PAYMENT_REQUIRED,
                "Assinatura inativa ou pagamento pendente. Regularize em /app/assinatura ou /app/pagamentos.");
    }

    @Transactional(readOnly = true)
    public boolean podeUtilizarPlataforma(Long idOrganizacao) {
        Plano plano = planoDaOrganizacao(idOrganizacao);
        if (planoGratuito(plano)) {
            return true;
        }

        return assinaturaRepository.findByIdOrganizacao(idOrganizacao)
                .map(this::assinaturaPermiteUso)
                .orElse(true);
    }

    private boolean assinaturaPermiteUso(OrganizacaoAssinatura assinatura) {
        return switch (assinatura.getStatus()) {
            case ATIVA -> true;
            case TRIAL -> assinatura.getDtFimTrial() == null
                    || assinatura.getDtFimTrial().isAfter(LocalDateTime.now());
            case PENDENTE, INADIMPLENTE, CANCELADA -> false;
        };
    }

    private Plano planoDaOrganizacao(Long idOrganizacao) {
        Organizacao organizacao = organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizacao nao encontrada."));
        if (organizacao.getIdPlano() == null) {
            return planoRepository.findAll().stream()
                    .filter(Plano::getFlAtivo)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Nenhum plano ativo disponivel."));
        }
        return planoRepository.findById(organizacao.getIdPlano())
                .filter(Plano::getFlAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Plano da organizacao inativo ou inexistente."));
    }

    private boolean planoGratuito(Plano plano) {
        return plano.getVlMensal() == null || plano.getVlMensal().compareTo(BigDecimal.ZERO) <= 0;
    }
}
