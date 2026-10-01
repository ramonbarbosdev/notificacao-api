ALTER TABLE organizacao_github_integracao
    ADD COLUMN IF NOT EXISTS ds_github_kanban_webhook_modo_envio varchar(20) NOT NULL DEFAULT 'LOTE',
    ADD COLUMN IF NOT EXISTS nu_github_kanban_webhook_intervalo_minutos integer NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS dt_github_kanban_webhook_ultimo_flush timestamp;

CREATE SEQUENCE IF NOT EXISTS seq_github_kanban_webhook_evento_pendente START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS github_kanban_webhook_evento_pendente (
    id_github_kanban_webhook_evento bigint NOT NULL DEFAULT nextval('seq_github_kanban_webhook_evento_pendente'),
    id_organizacao bigint NOT NULL,
    ds_delivery_id varchar(120),
    ds_chave_dedup varchar(500),
    ds_payload_json text NOT NULL,
    dt_ocorrido timestamp NOT NULL,
    dt_criacao timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_github_kanban_webhook_evento_pendente PRIMARY KEY (id_github_kanban_webhook_evento),
    CONSTRAINT fk_github_kanban_webhook_evento_org FOREIGN KEY (id_organizacao)
        REFERENCES organizacao (id_organizacao)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_github_kanban_webhook_evento_delivery
    ON github_kanban_webhook_evento_pendente (id_organizacao, ds_delivery_id)
    WHERE ds_delivery_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_github_kanban_webhook_evento_org_criacao
    ON github_kanban_webhook_evento_pendente (id_organizacao, dt_criacao);
