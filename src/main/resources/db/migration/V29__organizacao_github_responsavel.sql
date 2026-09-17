create sequence if not exists seq_organizacao_github_responsavel start with 1 increment by 1;

create table if not exists organizacao_github_responsavel (
    id_github_responsavel bigint not null default nextval('seq_organizacao_github_responsavel'),
    id_organizacao bigint not null,
    ds_github_login varchar(100),
    nu_whatsapp varchar(20) not null,
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp,
    constraint pk_organizacao_github_responsavel primary key (id_github_responsavel)
);

create unique index if not exists uk_org_github_whatsapp
    on organizacao_github_responsavel (id_organizacao, nu_whatsapp);

create unique index if not exists uk_org_github_login
    on organizacao_github_responsavel (id_organizacao, ds_github_login)
    where ds_github_login is not null;

create index if not exists idx_github_responsavel_org on organizacao_github_responsavel (id_organizacao);
