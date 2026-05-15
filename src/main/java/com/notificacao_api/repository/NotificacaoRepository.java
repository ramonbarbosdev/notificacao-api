package com.notificacao_api.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notificacao_api.enums.CanalNotificacao;
import com.notificacao_api.enums.StatusNotificacao;
import com.notificacao_api.model.Notificacao;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByIdOrganizacaoOrderByDtCriacaoDesc(Long idOrganizacao);

    @Query("""
            select n from Notificacao n
            where n.status = com.notificacao_api.enums.StatusNotificacao.PENDENTE
              and n.dtProximaTentativa <= :agora
            order by n.dtProximaTentativa asc, n.idNotificacao asc
            limit :limite
            """)
    List<Notificacao> buscarPendentesParaProcessar(
            @Param("agora") LocalDateTime agora,
            @Param("limite") int limite);

    @Modifying
    @Query("""
            update Notificacao n
               set n.status = com.notificacao_api.enums.StatusNotificacao.PROCESSANDO,
                   n.dtUltimoProcessamento = :agora
             where n.idNotificacao = :idNotificacao
               and n.status = com.notificacao_api.enums.StatusNotificacao.PENDENTE
               and n.dtProximaTentativa <= :agora
            """)
    int marcarProcessandoSePendente(
            @Param("idNotificacao") Long idNotificacao,
            @Param("agora") LocalDateTime agora);

    boolean existsByIdOrganizacaoAndCanalAndDestinatarioAndHashDeduplicacaoAndDtCriacaoAfterAndStatusIn(
            Long idOrganizacao,
            CanalNotificacao canal,
            String destinatario,
            String hashDeduplicacao,
            LocalDateTime criadoApos,
            List<StatusNotificacao> status);

    long countByIdOrganizacaoAndCanalAndStatusAndDtEnvioAfter(
            Long idOrganizacao,
            CanalNotificacao canal,
            StatusNotificacao status,
            LocalDateTime enviadoApos);
}
