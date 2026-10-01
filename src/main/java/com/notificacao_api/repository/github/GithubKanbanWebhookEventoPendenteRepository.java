package com.notificacao_api.repository.github;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notificacao_api.model.github.GithubKanbanWebhookEventoPendente;

public interface GithubKanbanWebhookEventoPendenteRepository
        extends JpaRepository<GithubKanbanWebhookEventoPendente, Long> {

    List<GithubKanbanWebhookEventoPendente> findByIdOrganizacaoOrderByDtOcorridoAsc(Long idOrganizacao);

    boolean existsByIdOrganizacaoAndDsChaveDedup(Long idOrganizacao, String dsChaveDedup);

    boolean existsByIdOrganizacaoAndDsDeliveryId(Long idOrganizacao, String dsDeliveryId);

    @Query(
            """
            SELECT DISTINCT p.idOrganizacao
            FROM GithubKanbanWebhookEventoPendente p
            """)
    List<Long> listarOrganizacoesComPendencias();

    void deleteByIdIn(List<Long> ids);

    @Query("SELECT COUNT(p) > 0 FROM GithubKanbanWebhookEventoPendente p WHERE p.idOrganizacao = :idOrganizacao")
    boolean existePendente(@Param("idOrganizacao") Long idOrganizacao);
}
