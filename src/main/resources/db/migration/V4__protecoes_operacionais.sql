alter table notificacao
    drop constraint if exists notificacao_tp_status_check;

update notificacao
set tp_status = case tp_status
    when 'PENDENTE' then 'PENDENTE'
    when 'ENVIADO' then 'ENVIADA'
    when 'ERRO' then 'FALHOU'
    else tp_status
end;

alter table notificacao add column if not exists nm_provedor varchar(255);
alter table notificacao add column if not exists nu_tentativas integer not null default 0;
alter table notificacao add column if not exists nu_tentativas_maximas integer not null default 3;
alter table notificacao add column if not exists ds_hash_deduplicacao varchar(128);
alter table notificacao add column if not exists dt_proxima_tentativa timestamp not null default current_timestamp;
alter table notificacao add column if not exists dt_ultimo_processamento timestamp;
alter table notificacao add column if not exists dt_envio timestamp;

alter table notificacao
    add constraint notificacao_tp_status_check
    check (tp_status in ('PENDENTE', 'PROCESSANDO', 'ENVIADA', 'ENTREGUE', 'LIDA', 'FALHOU', 'BLOQUEADA', 'CANCELADA'));

create index if not exists ix_notificacao_fila
    on notificacao(tp_status, dt_proxima_tentativa, id_notificacao);

create index if not exists ix_notificacao_limite_envio
    on notificacao(id_organizacao, tp_canal, tp_status, dt_envio);

create index if not exists ix_notificacao_deduplicacao
    on notificacao(id_organizacao, tp_canal, ds_destinatario, ds_hash_deduplicacao, dt_criacao);

alter table whatsapp_sessao add column if not exists tp_status_operacional varchar(30) not null default 'ATIVA';
alter table whatsapp_sessao add column if not exists dt_pausado_ate timestamp;
alter table whatsapp_sessao add column if not exists nu_falhas_consecutivas integer not null default 0;
alter table whatsapp_sessao add column if not exists dt_proximo_envio_apos timestamp;

alter table whatsapp_sessao
    drop constraint if exists whatsapp_sessao_tp_status_operacional_check;

alter table whatsapp_sessao
    add constraint whatsapp_sessao_tp_status_operacional_check
    check (tp_status_operacional in ('ATIVA', 'PAUSADA', 'DESCONECTADA', 'RISCO_BANIMENTO', 'BLOQUEADA'));

create sequence if not exists seq_contato_notificacao start with 1 increment by 1;

create table if not exists contato_notificacao (
    id_contato bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    tp_canal varchar(30) not null check (tp_canal in ('WHATSAPP', 'EMAIL', 'TELEGRAM', 'WEBHOOK')),
    ds_destinatario varchar(255) not null,
    fl_consentimento boolean not null default false,
    fl_bloqueado boolean not null default false,
    ds_motivo_bloqueio varchar(255),
    dt_consentimento timestamp,
    dt_bloqueio timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create unique index if not exists ux_contato_notificacao_destino
    on contato_notificacao(id_organizacao, tp_canal, ds_destinatario);

create sequence if not exists seq_notificacao_auditoria start with 1 increment by 1;

create table if not exists notificacao_auditoria (
    id_auditoria bigint primary key,
    id_notificacao bigint not null references notificacao(id_notificacao),
    id_organizacao bigint not null,
    tp_canal varchar(30) not null,
    ds_destinatario varchar(255) not null,
    tp_status varchar(30) not null,
    tp_evento varchar(30) not null,
    nm_provedor varchar(255),
    nu_tentativa integer not null default 0,
    ds_erro text,
    dt_criacao timestamp not null default current_timestamp
);

create index if not exists ix_notificacao_auditoria_notificacao
    on notificacao_auditoria(id_notificacao, dt_criacao);
