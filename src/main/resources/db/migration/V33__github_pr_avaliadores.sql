alter table organizacao_configuracao
    add column if not exists fl_github_pr_avisar_avaliadores boolean not null default false;

alter table organizacao_configuracao
    add column if not exists ds_github_pr_status_disparo varchar(500);

alter table organizacao_configuracao
    add column if not exists ds_github_pr_logins_avaliadores varchar(500);
