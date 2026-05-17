alter table public.notificacao_modelo
add column if not exists cd_chave varchar(255),
add column if not exists ds_variaveis_obrigatorias text not null default '[]',
add column if not exists nr_versao integer not null default 1;

update public.notificacao_modelo
set cd_chave = lower(regexp_replace(trim(nm_modelo), '[^a-zA-Z0-9]+', '-', 'g'))
where cd_chave is null;

alter table public.notificacao_modelo
alter column cd_chave set not null;

drop index if exists ux_notificacao_modelo_org_canal_nome;

create unique index if not exists ux_notificacao_modelo_org_chave
on public.notificacao_modelo(id_organizacao, cd_chave);

create index if not exists ix_notificacao_modelo_org_canal_ativo
on public.notificacao_modelo(id_organizacao, tp_canal, fl_ativo);
