alter table plano
    add column if not exists vl_mensal numeric(10, 2) not null default 0,
    add column if not exists nu_dias_trial integer not null default 0;

update plano
set vl_mensal = 0,
    nu_dias_trial = 14
where nm_plano = 'Demo';

alter table organizacao
    add column if not exists id_cliente_asaas varchar(50);

create sequence if not exists seq_organizacao_assinatura start with 1 increment by 1;
create sequence if not exists seq_organizacao_metodo_pagamento start with 1 increment by 1;
create sequence if not exists seq_organizacao_cobranca start with 1 increment by 1;
create sequence if not exists seq_pagamento_webhook_processado start with 1 increment by 1;

create table if not exists organizacao_assinatura (
    id_organizacao_assinatura bigint primary key,
    id_organizacao bigint not null unique references organizacao(id_organizacao),
    id_plano bigint not null references plano(id_plano),
    ds_status varchar(30) not null,
    ds_forma_pagamento varchar(20),
    id_assinatura_asaas varchar(50),
    dt_proximo_vencimento date,
    dt_fim_trial timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create index if not exists ix_organizacao_assinatura_status
    on organizacao_assinatura(ds_status);

create table if not exists organizacao_metodo_pagamento (
    id_metodo_pagamento bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    ds_tipo varchar(20) not null,
    id_cartao_asaas varchar(100) not null,
    nu_ultimos4 varchar(4),
    ds_bandeira varchar(30),
    fl_padrao boolean not null default false,
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create index if not exists ix_organizacao_metodo_pagamento_org
    on organizacao_metodo_pagamento(id_organizacao, fl_ativo);

create table if not exists organizacao_cobranca (
    id_organizacao_cobranca bigint primary key,
    id_organizacao bigint not null references organizacao(id_organizacao),
    id_cobranca_asaas varchar(50) not null,
    vl_cobranca numeric(10, 2) not null,
    ds_status varchar(30) not null,
    ds_pix_copia_cola text,
    ds_pix_qr_base64 text,
    dt_vencimento date,
    dt_pagamento timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create unique index if not exists ux_organizacao_cobranca_asaas
    on organizacao_cobranca(id_cobranca_asaas);

create index if not exists ix_organizacao_cobranca_org_status
    on organizacao_cobranca(id_organizacao, ds_status);

create table if not exists pagamento_webhook_processado (
    id_pagamento_webhook_processado bigint primary key,
    id_evento_asaas varchar(100) not null,
    ds_tipo_evento varchar(60) not null,
    dt_processamento timestamp not null default current_timestamp
);

create unique index if not exists ux_pagamento_webhook_evento
    on pagamento_webhook_processado(id_evento_asaas);
