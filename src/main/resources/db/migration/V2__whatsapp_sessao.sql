create sequence if not exists seq_whatsapp_sessao
start with 1
increment by 1;

create table if not exists whatsapp_sessao (
    id_whatsappsession bigint primary key,

    id_organizacao bigint not null
        references organizacao(id_organizacao),

    tp_status varchar(30) not null,

    nu_telefone varchar(50),

    ds_sessionpath varchar(255) not null,

    dt_ultimaconexao timestamp,

    dt_criacao timestamp not null default current_timestamp,

    dt_atualizacao timestamp not null default current_timestamp
);

create unique index if not exists ux_whatsapp_sessao_organizacao
    on whatsapp_sessao(id_organizacao);