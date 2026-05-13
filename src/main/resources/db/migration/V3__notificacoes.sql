create sequence if not exists seq_notificacao start with 1 increment by 1;
create sequence if not exists seq_notificacao_provider_config start with 1 increment by 1;
create sequence if not exists seq_notificacao_template start with 1 increment by 1;

create table if not exists notificacao_provider_config (
    id_providerconfig bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    tp_canal varchar(30) not null check (tp_canal in ('WHATSAPP', 'EMAIL', 'TELEGRAM', 'WEBHOOK')),
    nm_provider varchar(255) not null,
    fl_ativo boolean not null default true,
    ds_configuracoes text,
    ds_credenciais text,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create index if not exists ix_notificacao_provider_config_org_canal
    on notificacao_provider_config(id_organizacao, tp_canal, fl_ativo);

create table if not exists notificacao (
    id_notificacao bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    tp_canal varchar(30) not null check (tp_canal in ('WHATSAPP', 'EMAIL', 'TELEGRAM', 'WEBHOOK')),
    ds_destinatario varchar(255) not null,
    ds_assunto varchar(255),
    ds_mensagem text not null,
    tp_status varchar(30) not null check (tp_status in ('PENDENTE', 'ENVIADO', 'ERRO')),
    ds_erro text,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create index if not exists ix_notificacao_org_canal_status
    on notificacao(id_organizacao, tp_canal, tp_status);

create table if not exists notificacao_template (
    id_template bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    tp_canal varchar(30) not null check (tp_canal in ('WHATSAPP', 'EMAIL', 'TELEGRAM', 'WEBHOOK')),
    nm_template varchar(255) not null,
    ds_assunto varchar(255),
    ds_corpo text not null,
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create unique index if not exists ux_notificacao_template_org_canal_nome
    on notificacao_template(id_organizacao, tp_canal, nm_template);

insert into notificacao_provider_config (
    id_providerconfig,
    id_organizacao,
    tp_canal,
    nm_provider,
    fl_ativo,
    ds_configuracoes,
    ds_credenciais,
    dt_criacao,
    dt_atualizacao
)
select 1, 1, 'WHATSAPP', 'WHATSAPP_GATEWAY', true, '{}', null, current_timestamp, current_timestamp
where not exists (
    select 1 from notificacao_provider_config where id_organizacao = 1 and tp_canal = 'WHATSAPP'
);

insert into notificacao_provider_config (
    id_providerconfig,
    id_organizacao,
    tp_canal,
    nm_provider,
    fl_ativo,
    ds_configuracoes,
    ds_credenciais,
    dt_criacao,
    dt_atualizacao
)
select 2, 1, 'EMAIL', 'SIMULADO', true, '{}', null, current_timestamp, current_timestamp
where not exists (
    select 1 from notificacao_provider_config where id_organizacao = 1 and tp_canal = 'EMAIL'
);

insert into notificacao_provider_config (
    id_providerconfig,
    id_organizacao,
    tp_canal,
    nm_provider,
    fl_ativo,
    ds_configuracoes,
    ds_credenciais,
    dt_criacao,
    dt_atualizacao
)
select 3, 1, 'TELEGRAM', 'SIMULADO', true, '{}', null, current_timestamp, current_timestamp
where not exists (
    select 1 from notificacao_provider_config where id_organizacao = 1 and tp_canal = 'TELEGRAM'
);

insert into notificacao_provider_config (
    id_providerconfig,
    id_organizacao,
    tp_canal,
    nm_provider,
    fl_ativo,
    ds_configuracoes,
    ds_credenciais,
    dt_criacao,
    dt_atualizacao
)
select 4, 1, 'WEBHOOK', 'SIMULADO', true, '{}', null, current_timestamp, current_timestamp
where not exists (
    select 1 from notificacao_provider_config where id_organizacao = 1 and tp_canal = 'WEBHOOK'
);
