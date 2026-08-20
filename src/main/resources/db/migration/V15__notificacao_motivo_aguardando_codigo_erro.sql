ALTER TABLE notificacao
    ADD COLUMN IF NOT EXISTS ds_motivo_aguardando TEXT;

ALTER TABLE notificacao
    ADD COLUMN IF NOT EXISTS ds_codigo_erro VARCHAR(64);
