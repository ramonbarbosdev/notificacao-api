alter table public.notificacao_modelo
add column if not exists ds_variaveis text not null default '[]';

update public.notificacao_modelo
set ds_variaveis = (
    select coalesce(json_agg(json_build_object(
        'chave', variavel,
        'label', initcap(replace(replace(variavel, '_', ' '), '-', ' ')),
        'tipo', 'TEXTO',
        'obrigatoria', true,
        'exemplo', null
    ))::text, '[]')
    from json_array_elements_text(ds_variaveis_obrigatorias::json) as variavel
)
where ds_variaveis = '[]'
  and ds_variaveis_obrigatorias is not null
  and ds_variaveis_obrigatorias <> '[]';
