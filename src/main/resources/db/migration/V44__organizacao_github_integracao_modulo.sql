CREATE TABLE IF NOT EXISTS organizacao_github_integracao (
    id_organizacao BIGINT PRIMARY KEY REFERENCES organizacao (id_organizacao) ON DELETE CASCADE,
    ds_frase_ativacao_whatsapp VARCHAR(500),
    ds_organization_login VARCHAR(120),
    ds_graphql_token_enc TEXT,
    nu_github_app_id BIGINT,
    ds_github_app_private_key_enc TEXT,
    nu_github_installation_id BIGINT,
    ds_github_graphql_url VARCHAR(500),
    ds_github_api_base_url VARCHAR(500),
    nu_github_http_connect_timeout_ms INTEGER,
    nu_github_http_read_timeout_ms INTEGER,
    nu_github_installation_token_skew_segundos INTEGER,
    dt_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dt_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE IF NOT EXISTS seq_organizacao_github_modulo START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS organizacao_github_modulo (
    id_github_modulo BIGINT PRIMARY KEY DEFAULT nextval('seq_organizacao_github_modulo'),
    id_organizacao BIGINT NOT NULL REFERENCES organizacao (id_organizacao) ON DELETE CASCADE,
    ds_modulo VARCHAR(40) NOT NULL,
    fl_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    ds_config_json TEXT,
    dt_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dt_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_org_github_modulo UNIQUE (id_organizacao, ds_modulo)
);

CREATE INDEX IF NOT EXISTS ix_github_modulo_org ON organizacao_github_modulo (id_organizacao);

INSERT INTO organizacao_github_integracao (
    id_organizacao,
    ds_frase_ativacao_whatsapp,
    ds_organization_login,
    ds_graphql_token_enc,
    nu_github_app_id,
    ds_github_app_private_key_enc,
    nu_github_installation_id,
    ds_github_graphql_url,
    ds_github_api_base_url,
    nu_github_http_connect_timeout_ms,
    nu_github_http_read_timeout_ms,
    nu_github_installation_token_skew_segundos,
    dt_criacao,
    dt_atualizacao
)
SELECT
    oc.id_organizacao,
    oc.ds_github_frase_ativacao_whatsapp,
    oc.ds_github_organization_login,
    oc.ds_github_graphql_token_enc,
    oc.nu_github_app_id,
    oc.ds_github_app_private_key_enc,
    oc.nu_github_installation_id,
    oc.ds_github_graphql_url,
    oc.ds_github_api_base_url,
    oc.nu_github_http_connect_timeout_ms,
    oc.nu_github_http_read_timeout_ms,
    oc.nu_github_installation_token_skew_segundos,
    COALESCE(oc.dt_criacao, CURRENT_TIMESTAMP),
    COALESCE(oc.dt_atualizacao, CURRENT_TIMESTAMP)
FROM organizacao_configuracao oc
ON CONFLICT (id_organizacao) DO NOTHING;

INSERT INTO organizacao_github_modulo (id_organizacao, ds_modulo, fl_habilitado, ds_config_json, dt_criacao, dt_atualizacao)
SELECT
    oc.id_organizacao,
    'PROJECTS_V2',
    TRUE,
    json_build_object(
        'versao', 1,
        'projectV2NodeId', oc.ds_github_project_v2_node_id,
        'projectV2Number', oc.nu_github_project_v2_number,
        'statusDisparo', oc.ds_github_status_disparo,
        'statusDisparoGatilhos', oc.ds_github_status_disparo_gatilhos,
        'regrasPorStatus', oc.ds_github_regras_por_status,
        'templateAssuntoWhatsapp', oc.ds_github_template_assunto_whatsapp,
        'templateMensagemWhatsapp', oc.ds_github_template_mensagem_whatsapp,
        'templatesPorCenario', COALESCE(oc.ds_github_templates_por_cenario::json, '{}'::json),
        'naoNotificarMovimentador', COALESCE(oc.fl_github_nao_notificar_movimentador, TRUE),
        'notificarStatusAlterado', COALESCE(oc.fl_github_notificar_status_alterado, TRUE),
        'notificarTarefaCriada', COALESCE(oc.fl_github_notificar_tarefa_criada, FALSE),
        'notificarResponsavelAlterado', COALESCE(oc.fl_github_notificar_responsavel_alterado, FALSE),
        'notificarTarefaAtribuida', COALESCE(oc.fl_github_notificar_tarefa_atribuida, FALSE),
        'ignorarSemResponsavel', COALESCE(oc.fl_github_ignorar_sem_responsavel, TRUE),
        'destinatariosModo', COALESCE(oc.ds_github_destinatarios_modo, 'RESPONSAVEIS'),
        'destinatariosExtras', oc.ds_github_destinatarios_extras,
        'notificarIssueFechadaReaberta', COALESCE(oc.fl_github_notificar_issue_fechada_reaberta, FALSE),
        'notificarIssueLabel', COALESCE(oc.fl_github_notificar_issue_label, FALSE),
        'notificarSomenteCampoStatus', COALESCE(oc.fl_github_notificar_somente_campo_status, FALSE),
        'notificarReordenacao', COALESCE(oc.fl_github_notificar_reordenacao, FALSE),
        'prAvisarAvaliadores', COALESCE(oc.fl_github_pr_avisar_avaliadores, FALSE),
        'prStatusDisparo', oc.ds_github_pr_status_disparo,
        'prLoginsAvaliadores', oc.ds_github_pr_logins_avaliadores,
        'issueAvisarAvaliadores', COALESCE(oc.fl_github_issue_avisar_avaliadores, FALSE),
        'issueStatusDisparo', oc.ds_github_issue_status_disparo
    )::TEXT,
    COALESCE(oc.dt_criacao, CURRENT_TIMESTAMP),
    COALESCE(oc.dt_atualizacao, CURRENT_TIMESTAMP)
FROM organizacao_configuracao oc
ON CONFLICT (id_organizacao, ds_modulo) DO NOTHING;

INSERT INTO organizacao_github_modulo (id_organizacao, ds_modulo, fl_habilitado, ds_config_json, dt_criacao, dt_atualizacao)
SELECT
    oc.id_organizacao,
    'ISSUE_COMMENT',
    FALSE,
    '{"versao":1}',
    COALESCE(oc.dt_criacao, CURRENT_TIMESTAMP),
    COALESCE(oc.dt_atualizacao, CURRENT_TIMESTAMP)
FROM organizacao_configuracao oc
ON CONFLICT (id_organizacao, ds_modulo) DO NOTHING;
