create table if not exists whatsapp_conversa_oculta (
    id_organizacao bigint not null references organizacao(id_organizacao),
    ds_telefone varchar(32) not null,
    dt_oculta timestamp not null default current_timestamp,
    primary key (id_organizacao, ds_telefone)
);

create index if not exists ix_whatsapp_conversa_oculta_org
    on whatsapp_conversa_oculta(id_organizacao);
