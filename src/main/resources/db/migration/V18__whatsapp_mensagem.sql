create sequence if not exists seq_whatsapp_mensagem start with 1 increment by 1;
create sequence if not exists seq_whatsapp_webhook_evento start with 1 increment by 1;

create table if not exists whatsapp_mensagem (
    id_mensagem bigint primary key default nextval('seq_whatsapp_mensagem'),
    id_organizacao bigint not null references organizacao(id_organizacao),
    id_notificacao bigint references notificacao(id_notificacao),
    tp_provider varchar(30) not null,
    ds_telefone varchar(32) not null,
    tp_direcao varchar(16) not null
        check (tp_direcao in ('OUTBOUND', 'INBOUND')),
    tp_mensagem varchar(16) not null
        check (tp_mensagem in ('TEXT', 'TEMPLATE', 'IMAGE', 'DOCUMENT')),
    ds_categoria varchar(64),
    nm_template varchar(255),
    ds_id_externo varchar(128),
    tp_status varchar(16) not null default 'PENDING'
        check (tp_status in ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    ds_codigo_erro varchar(64),
    ds_erro text,
    dt_envio timestamp,
    dt_entrega timestamp,
    dt_leitura timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

alter sequence seq_whatsapp_mensagem owned by whatsapp_mensagem.id_mensagem;

create index if not exists ix_whatsapp_mensagem_org
    on whatsapp_mensagem(id_organizacao);

create index if not exists ix_whatsapp_mensagem_id_externo
    on whatsapp_mensagem(ds_id_externo);

create index if not exists ix_whatsapp_mensagem_status
    on whatsapp_mensagem(tp_status);

create index if not exists ix_whatsapp_mensagem_criacao
    on whatsapp_mensagem(dt_criacao);

create table if not exists whatsapp_webhook_evento (
    id_evento bigint primary key default nextval('seq_whatsapp_webhook_evento'),
    ds_id_evento_meta varchar(255) not null,
    dt_processado timestamp not null default current_timestamp
);

alter sequence seq_whatsapp_webhook_evento owned by whatsapp_webhook_evento.id_evento;

create unique index if not exists ux_whatsapp_webhook_evento_meta
    on whatsapp_webhook_evento(ds_id_evento_meta);
