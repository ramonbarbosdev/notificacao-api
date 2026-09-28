alter table organizacao_configuracao
    add column if not exists fl_envio_mensagens_habilitado boolean not null default true;
