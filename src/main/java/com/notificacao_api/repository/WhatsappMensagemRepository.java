package com.notificacao_api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notificacao_api.model.WhatsappMensagem;

public interface WhatsappMensagemRepository extends JpaRepository<WhatsappMensagem, Long> {

    Optional<WhatsappMensagem> findByIdOrganizacaoAndIdExterno(Long idOrganizacao, String idExterno);

    Optional<WhatsappMensagem> findByIdExterno(String idExterno);

    List<WhatsappMensagem> findByIdOrganizacaoAndTelefoneInOrderByDtCriacaoAsc(
            Long idOrganizacao,
            Collection<String> telefones,
            Pageable pageable);

    long countByIdOrganizacaoAndTelefoneIn(Long idOrganizacao, Collection<String> telefones);

    @Query("SELECT DISTINCT m.telefone FROM WhatsappMensagem m WHERE m.idOrganizacao = :idOrganizacao")
    List<String> findDistinctTelefonesByIdOrganizacao(@Param("idOrganizacao") Long idOrganizacao);

    @Modifying
    @Query("UPDATE WhatsappMensagem m SET m.telefone = :telefoneCorreto "
            + "WHERE m.idOrganizacao = :idOrganizacao AND m.telefone = :telefoneErrado")
    int atualizarTelefone(
            @Param("idOrganizacao") Long idOrganizacao,
            @Param("telefoneErrado") String telefoneErrado,
            @Param("telefoneCorreto") String telefoneCorreto);
}
