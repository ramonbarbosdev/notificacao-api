create sequence if not exists seq_github_webhook_decisao_log start with 1 increment by 1;

create table if not exists github_webhook_decisao_log (
    id_github_webhook_decisao_log bigint primary key,
    id_organizacao bigint not null,
    ds_delivery_id varchar(120),
    ds_github_event varchar(80),
    ds_action varchar(80),
    ds_resultado varchar(60) not null,
    ds_descricao text not null,
    ds_titulo_card varchar(500),
    ds_status_destino varchar(200),
    ds_status_anterior varchar(200),
    fl_pull_request boolean not null default false,
    fl_issue_project_v2 boolean not null default false,
    ds_fluxo_destinatarios varchar(40),
    ds_logins_destino text,
    nu_whatsapp_enfileirados integer not null default 0,
    ds_detalhe_json text,
    dt_criacao timestamp not null default current_timestamp
);

create index if not exists ix_github_webhook_decisao_org_data
    on github_webhook_decisao_log (id_organizacao, dt_criacao desc);
