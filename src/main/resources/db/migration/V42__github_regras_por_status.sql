ALTER TABLE organizacao_configuracao
    ADD COLUMN ds_github_regras_por_status TEXT NULL;

COMMENT ON COLUMN organizacao_configuracao.ds_github_regras_por_status IS
    'JSON versionado: regras por optionId de coluna Status do Project v2 (fluxo ao entrar, destinatarios, template)';
