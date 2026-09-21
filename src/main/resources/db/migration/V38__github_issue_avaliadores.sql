alter table organizacao_configuracao
    add column if not exists fl_github_issue_avisar_avaliadores boolean not null default false;

alter table organizacao_configuracao
    add column if not exists ds_github_issue_status_disparo varchar(500);
