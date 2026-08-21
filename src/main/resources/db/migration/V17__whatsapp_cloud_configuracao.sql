create sequence if not exists seq_whatsapp_configuracao start with 1 increment by 1;
create sequence if not exists seq_whatsapp_credencial start with 1 increment by 1;

create table if not exists whatsapp_configuracao (
    id_configuracao bigint primary key default nextval('seq_whatsapp_configuracao'),
    id_organizacao bigint not null references organizacao(id_organizacao),
    tp_provider varchar(30) not null default 'META_CLOUD'
        check (tp_provider in ('META_CLOUD')),
    ds_phone_number_id varchar(64) not null,
    ds_waba_id varchar(64),
    ds_api_version varchar(16) not null default 'v21.0',
    fl_ativo boolean not null default true,
    dt_ultimo_teste timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

alter sequence seq_whatsapp_configuracao owned by whatsapp_configuracao.id_configuracao;

create unique index if not exists ux_whatsapp_configuracao_org
    on whatsapp_configuracao(id_organizacao);

create index if not exists ix_whatsapp_configuracao_phone_ativo
    on whatsapp_configuracao(ds_phone_number_id, fl_ativo);

create table if not exists whatsapp_credencial (
    id_credencial bigint primary key default nextval('seq_whatsapp_credencial'),
    id_configuracao bigint not null unique references whatsapp_configuracao(id_configuracao),
    ds_access_token_criptografado text not null,
    dt_token_expira timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

alter sequence seq_whatsapp_credencial owned by whatsapp_credencial.id_credencial;
