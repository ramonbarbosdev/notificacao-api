alter table organizacao_configuracao
    add column if not exists fl_tctoken_confirmacao_automatica boolean not null default true;

alter table organizacao_configuracao
    add column if not exists nu_tctoken_confirmacao_automatica_dias_antes integer not null default 7;

alter table organizacao_configuracao
    add column if not exists ds_tctoken_confirmacao_mensagem_padrao text;
