DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'organizacao_github_integracao'
          AND column_name = 'fl_seven_webhook_habilitado'
    ) THEN
        ALTER TABLE organizacao_github_integracao
            RENAME COLUMN fl_seven_webhook_habilitado TO fl_github_kanban_movimentacao_webhook_habilitado;
        ALTER TABLE organizacao_github_integracao
            RENAME COLUMN ds_seven_webhook_url TO ds_github_kanban_movimentacao_webhook_url;
        ALTER TABLE organizacao_github_integracao
            RENAME COLUMN ds_seven_webhook_auth_enc TO ds_github_kanban_movimentacao_webhook_auth_enc;
    END IF;
END $$;

ALTER TABLE organizacao_github_integracao
    ADD COLUMN IF NOT EXISTS fl_github_kanban_movimentacao_webhook_habilitado boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS ds_github_kanban_movimentacao_webhook_url varchar(500),
    ADD COLUMN IF NOT EXISTS ds_github_kanban_movimentacao_webhook_auth_enc varchar(2000),
    ADD COLUMN IF NOT EXISTS fl_github_whatsapp_direto_habilitado boolean NOT NULL DEFAULT true;
