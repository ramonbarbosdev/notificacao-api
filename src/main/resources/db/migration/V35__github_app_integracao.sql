ALTER TABLE organizacao_configuracao
    ADD COLUMN IF NOT EXISTS nu_github_app_id BIGINT,
    ADD COLUMN IF NOT EXISTS ds_github_app_private_key_enc TEXT,
    ADD COLUMN IF NOT EXISTS nu_github_installation_id BIGINT,
    ADD COLUMN IF NOT EXISTS ds_github_graphql_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ds_github_api_base_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS nu_github_http_connect_timeout_ms INTEGER,
    ADD COLUMN IF NOT EXISTS nu_github_http_read_timeout_ms INTEGER,
    ADD COLUMN IF NOT EXISTS nu_github_installation_token_skew_segundos INTEGER;
