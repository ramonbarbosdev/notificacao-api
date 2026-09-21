ALTER TABLE organizacao_configuracao
    ADD COLUMN ds_github_status_disparo_gatilhos VARCHAR(500) NULL;

COMMENT ON COLUMN organizacao_configuracao.ds_github_status_disparo_gatilhos IS
    'Gatilhos (enum GithubWebhookRegrasNotificacao.Gatilho, virgula) aos quais aplica ds_github_status_disparo. NULL = STATUS_ALTERADO,REORDENADO';
