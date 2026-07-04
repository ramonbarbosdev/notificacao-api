CREATE SEQUENCE IF NOT EXISTS seq_alerta_operacional START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS alerta_operacional (
    id_alerta BIGINT PRIMARY KEY DEFAULT nextval('seq_alerta_operacional'),
    id_organizacao BIGINT,
    id_notificacao BIGINT,
    tp_origem VARCHAR(50) NOT NULL,
    ds_titulo VARCHAR(255) NOT NULL,
    ds_mensagem TEXT NOT NULL,
    ds_destinatario VARCHAR(255),
    ds_canal VARCHAR(30),
    ds_codigo_erro VARCHAR(80),
    fl_email_enviado BOOLEAN NOT NULL DEFAULT FALSE,
    dt_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alerta_operacional_org ON alerta_operacional (id_organizacao, dt_criacao DESC);

ALTER TABLE organizacao_configuracao ADD COLUMN IF NOT EXISTS ds_email_alertas VARCHAR(255);
ALTER TABLE configuracao_global ADD COLUMN IF NOT EXISTS nm_email_alertas VARCHAR(255);
