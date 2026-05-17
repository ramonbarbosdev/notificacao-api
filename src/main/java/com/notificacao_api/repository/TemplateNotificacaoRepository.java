package com.notificacao_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.notificacao_api.model.TemplateNotificacao;

public interface TemplateNotificacaoRepository
        extends JpaRepository<TemplateNotificacao, Long>, JpaSpecificationExecutor<TemplateNotificacao> {

    Optional<TemplateNotificacao> findByIdOrganizacaoAndChave(Long idOrganizacao, String chave);

    boolean existsByIdOrganizacaoAndChave(Long idOrganizacao, String chave);

    boolean existsByIdOrganizacaoAndChaveAndIdModeloNot(Long idOrganizacao, String chave, Long idModelo);

    long countByIdOrganizacao(Long idOrganizacao);
}
