alter table whatsapp_mensagem
    add column if not exists ds_conteudo text;

create sequence if not exists seq_whatsapp_conversa start with 1 increment by 1;

create table if not exists whatsapp_conversa (
    id_conversa bigint primary key default nextval('seq_whatsapp_conversa'),
    id_organizacao bigint not null references organizacao(id_organizacao),
    ds_telefone varchar(32) not null,
    nm_contato varchar(255),
    ds_ultima_mensagem text,
    tp_ultima_mensagem varchar(32),
    ds_jid varchar(128),
    fl_nao_lida boolean not null default true,
    dt_ultima_mensagem timestamp not null default current_timestamp,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

alter sequence seq_whatsapp_conversa owned by whatsapp_conversa.id_conversa;

create unique index if not exists ux_whatsapp_conversa_org_telefone
    on whatsapp_conversa(id_organizacao, ds_telefone);

create index if not exists ix_whatsapp_conversa_org_ultima
    on whatsapp_conversa(id_organizacao, dt_ultima_mensagem desc);
