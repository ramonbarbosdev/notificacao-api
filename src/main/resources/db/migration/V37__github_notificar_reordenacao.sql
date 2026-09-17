alter table organizacao_configuracao
    add column if not exists fl_github_notificar_reordenacao boolean not null default false;
