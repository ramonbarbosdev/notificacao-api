create sequence if not exists seq_configuracao_global start with 1 increment by 1;
create sequence if not exists seq_plano start with 1 increment by 1;
create sequence if not exists seq_organizacao_feature_flag start with 1 increment by 1;
create sequence if not exists seq_organizacao_configuracao start with 1 increment by 1;
create sequence if not exists seq_organizacao_api_key start with 1 increment by 1;
create sequence if not exists seq_organizacao_webhook start with 1 increment by 1;
create sequence if not exists seq_auditoria_evento start with 1 increment by 1;

create table if not exists configuracao_global (
    id_configuracao_global bigint primary key,
    nm_plataforma varchar(255) not null,
    nm_dominio_principal varchar(255),
    nm_email_suporte varchar(255),
    ds_smtp_host varchar(255),
    nu_smtp_porta integer,
    nm_smtp_usuario varchar(255),
    ds_smtp_senha_criptografada varchar(255),
    nu_timezone_padrao integer,
    fl_whatsapp_provider_padrao boolean not null default true,
    fl_api_publica_habilitada boolean not null default false,
    fl_templates_habilitado boolean not null default true,
    fl_webhooks_habilitado boolean not null default true,
    fl_telegram_habilitado boolean not null default false,
    fl_email_habilitado boolean not null default false,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create table if not exists plano (
    id_plano bigint primary key,
    nm_plano varchar(255) not null,
    ds_plano text,
    nu_limite_mensagens_mensal integer,
    nu_limite_usuarios integer,
    nu_limite_templates integer,
    nu_limite_contatos integer,
    fl_whatsapp_habilitado boolean not null default true,
    fl_email_habilitado boolean not null default false,
    fl_telegram_habilitado boolean not null default false,
    fl_webhook_habilitado boolean not null default true,
    fl_api_publica_habilitada boolean not null default false,
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

alter table public.organizacao
add column if not exists id_plano bigint;

create table if not exists organizacao_feature_flag (
    id_feature_flag bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    ds_recurso varchar(50) not null,
    fl_habilitado boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create unique index if not exists ux_organizacao_feature_flag_org_recurso
on organizacao_feature_flag(id_organizacao, ds_recurso);

create table if not exists organizacao_configuracao (
    id_organizacao_configuracao bigint primary key,
    id_organizacao bigint not null unique references organizacao(id_organizacao),
    nm_exibicao varchar(255),
    ds_logo_url varchar(500),
    ds_idioma varchar(20) default 'pt-BR',
    ds_timezone varchar(100) default 'America/Bahia',
    nu_telefone_operacional varchar(30),
    ds_email_operacional varchar(255),
    fl_whatsapp_reconexao_automatica boolean not null default true,
    nu_whatsapp_delay_min_segundos integer default 2,
    nu_whatsapp_delay_max_segundos integer default 8,
    fl_whatsapp_simular_digitando boolean not null default true,
    nu_whatsapp_limite_por_minuto integer default 20,
    nu_whatsapp_limite_por_dia integer default 1000,
    ds_whatsapp_modo_envio varchar(30) default 'FILA',
    fl_exigir_consentimento boolean not null default true,
    fl_consentimento_expira boolean not null default false,
    nu_dias_expiracao_consentimento integer,
    fl_bloqueio_automatico boolean not null default true,
    nu_limite_falhas_para_bloqueio integer default 5,
    fl_templates_versionamento boolean not null default true,
    fl_templates_exigir_aprovacao boolean not null default false,
    fl_templates_validar_variaveis boolean not null default true,
    fl_retry_automatico boolean not null default true,
    nu_retry_tentativas integer default 3,
    nu_retry_intervalo_segundos integer default 60,
    ds_prioridade_padrao varchar(30) default 'NORMAL',
    nu_expiracao_fila_horas integer default 24,
    fl_auditoria_habilitada boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create table if not exists organizacao_api_key (
    id_api_key bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    nm_api_key varchar(255) not null,
    ds_prefixo varchar(20) not null,
    ds_hash_chave varchar(255) not null,
    ds_scopes text not null,
    fl_ativo boolean not null default true,
    dt_ultimo_uso timestamp,
    dt_expira timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_revogacao timestamp
);

create index if not exists ix_organizacao_api_key_org_ativo
on organizacao_api_key(id_organizacao, fl_ativo);

create table if not exists organizacao_webhook (
    id_webhook bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    nm_webhook varchar(255) not null,
    ds_url varchar(500) not null,
    ds_secret_hash varchar(255) not null,
    ds_eventos text not null,
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create index if not exists ix_organizacao_webhook_org_ativo
on organizacao_webhook(id_organizacao, fl_ativo);

create table if not exists auditoria_evento (
    id_auditoria bigint primary key,
    id_organizacao bigint,
    id_usuario bigint,
    ds_role varchar(50),
    nm_modulo varchar(100) not null,
    nm_acao varchar(100) not null,
    ds_descricao text,
    ds_ip varchar(80),
    ds_user_agent varchar(500),
    ds_dados_antes text,
    ds_dados_depois text,
    dt_criacao timestamp not null default current_timestamp
);

create index if not exists ix_auditoria_evento_org_data
on auditoria_evento(id_organizacao, dt_criacao desc);

insert into plano (
    id_plano,
    nm_plano,
    ds_plano,
    nu_limite_mensagens_mensal,
    nu_limite_usuarios,
    nu_limite_templates,
    nu_limite_contatos,
    fl_whatsapp_habilitado,
    fl_webhook_habilitado,
    fl_ativo,
    dt_criacao,
    dt_atualizacao
)
select 1, 'Demo', 'Plano padrao criado pela migracao inicial de configuracoes.', 10000, 10, 100, 10000, true, true, true, current_timestamp, current_timestamp
where not exists (select 1 from plano where id_plano = 1);

update organizacao
set id_plano = 1
where id_plano is null;

insert into organizacao_configuracao (
    id_organizacao_configuracao,
    id_organizacao,
    nm_exibicao,
    dt_criacao,
    dt_atualizacao
)
select nextval('seq_organizacao_configuracao'), o.id_organizacao, o.nm_organizacao, current_timestamp, current_timestamp
from organizacao o
where not exists (
    select 1 from organizacao_configuracao c where c.id_organizacao = o.id_organizacao
);

select setval('seq_plano', coalesce((select max(id_plano) from plano), 0) + 1, false);
select setval(
    'seq_organizacao_configuracao',
    coalesce((select max(id_organizacao_configuracao) from organizacao_configuracao), 0) + 1,
    false
);
