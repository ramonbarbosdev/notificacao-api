package com.notificacao_api.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notificacao_api.model.WhatsappConversaOculta;

public interface WhatsappConversaOcultaRepository extends JpaRepository<WhatsappConversaOculta, WhatsappConversaOculta.WhatsappConversaOcultaId> {

    boolean existsByIdOrganizacaoAndTelefone(Long idOrganizacao, String telefone);

    void deleteByIdOrganizacaoAndTelefone(Long idOrganizacao, String telefone);

    @Query("""
            select o.telefone
            from WhatsappConversaOculta o
            where o.idOrganizacao = :idOrganizacao
            """)
    Set<String> findTelefonesByIdOrganizacao(@Param("idOrganizacao") Long idOrganizacao);
}
