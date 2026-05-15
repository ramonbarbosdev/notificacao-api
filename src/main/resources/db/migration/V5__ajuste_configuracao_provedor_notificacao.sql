-- V20260515_01__ajusta_configuracao_provedor_notificacao.sql

-- 1. Renomeia sequence antiga para o novo padrão da Entity
do $$
begin
    if exists (
        select 1 from pg_class where relkind = 'S' and relname = 'seq_notificacao_provider_config'
    ) and not exists (
        select 1 from pg_class where relkind = 'S' and relname = 'seq_notificacao_configuracao_provedor'
    ) then
        alter sequence seq_notificacao_provider_config
        rename to seq_notificacao_configuracao_provedor;
    end if;
end $$;

create sequence if not exists seq_notificacao_configuracao_provedor
    start with 1
    increment by 1;

-- 2. Renomeia tabela antiga para o novo nome da Entity
do $$
begin
    if exists (
        select 1 from information_schema.tables
        where table_schema = 'public'
          and table_name = 'notificacao_provider_config'
    ) and not exists (
        select 1 from information_schema.tables
        where table_schema = 'public'
          and table_name = 'notificacao_configuracao_provedor'
    ) then
        alter table public.notificacao_provider_config
        rename to notificacao_configuracao_provedor;
    end if;
end $$;

-- 3. Renomeia colunas antigas para o novo padrão Java/JPA
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'notificacao_configuracao_provedor'
          and column_name = 'id_providerconfig'
    ) then
        alter table public.notificacao_configuracao_provedor
        rename column id_providerconfig to id_configuracao_provedor;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'notificacao_configuracao_provedor'
          and column_name = 'nm_provider'
    ) then
        alter table public.notificacao_configuracao_provedor
        rename column nm_provider to nm_provedor;
    end if;
end $$;

-- 4. Ajusta default do ID para usar a nova sequence
alter table public.notificacao_configuracao_provedor
alter column id_configuracao_provedor
set default nextval('seq_notificacao_configuracao_provedor');

alter sequence seq_notificacao_configuracao_provedor
owned by public.notificacao_configuracao_provedor.id_configuracao_provedor;

-- 5. Sincroniza sequence com os registros já existentes
select setval(
    'seq_notificacao_configuracao_provedor',
    coalesce((select max(id_configuracao_provedor) from public.notificacao_configuracao_provedor), 0) + 1,
    false
);

-- 6. Recria índice com novo nome
drop index if exists ix_notificacao_provider_config_org_canal;

create index if not exists ix_notificacao_configuracao_provedor_org_canal
on public.notificacao_configuracao_provedor(id_organizacao, tp_canal, fl_ativo);