-- V20260515_02__ajusta_modelo_notificacao.sql

do $$
begin
    if exists (
        select 1 from pg_class
        where relkind = 'S'
          and relname = 'seq_notificacao_template'
    ) and not exists (
        select 1 from pg_class
        where relkind = 'S'
          and relname = 'seq_notificacao_modelo'
    ) then
        alter sequence seq_notificacao_template
        rename to seq_notificacao_modelo;
    end if;
end $$;

create sequence if not exists seq_notificacao_modelo
    start with 1
    increment by 1;

do $$
begin
    if exists (
        select 1 from information_schema.tables
        where table_schema = 'public'
          and table_name = 'notificacao_template'
    ) and not exists (
        select 1 from information_schema.tables
        where table_schema = 'public'
          and table_name = 'notificacao_modelo'
    ) then
        alter table public.notificacao_template
        rename to notificacao_modelo;
    end if;
end $$;

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'notificacao_modelo'
          and column_name = 'id_template'
    ) then
        alter table public.notificacao_modelo
        rename column id_template to id_modelo;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'notificacao_modelo'
          and column_name = 'nm_template'
    ) then
        alter table public.notificacao_modelo
        rename column nm_template to nm_modelo;
    end if;
end $$;

alter table public.notificacao_modelo
alter column id_modelo
set default nextval('seq_notificacao_modelo');

alter sequence seq_notificacao_modelo
owned by public.notificacao_modelo.id_modelo;

select setval(
    'seq_notificacao_modelo',
    coalesce((select max(id_modelo) from public.notificacao_modelo), 0) + 1,
    false
);

drop index if exists ux_notificacao_template_org_canal_nome;

create unique index if not exists ux_notificacao_modelo_org_canal_nome
on public.notificacao_modelo(id_organizacao, tp_canal, nm_modelo);