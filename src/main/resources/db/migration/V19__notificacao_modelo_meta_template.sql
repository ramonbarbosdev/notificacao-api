alter table public.notificacao_modelo
    add column if not exists nm_meta_template varchar(255);

alter table public.notificacao_modelo
    add column if not exists cd_meta_idioma varchar(16);

alter table public.notificacao
    add column if not exists cd_chave_modelo varchar(128);

alter table public.notificacao
    add column if not exists ds_variaveis_template text;
