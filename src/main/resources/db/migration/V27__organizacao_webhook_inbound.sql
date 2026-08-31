alter table organizacao_configuracao
    add column if not exists ds_webhook_inbound_url varchar(500),
    add column if not exists ds_webhook_inbound_secret_enc varchar(1000),
    add column if not exists fl_webhook_inbound_habilitado boolean not null default false;
