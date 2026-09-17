alter table organizacao_configuracao
    add column if not exists ds_github_templates_por_cenario text;
