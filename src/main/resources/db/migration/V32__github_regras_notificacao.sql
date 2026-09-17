alter table organizacao_configuracao
    add column if not exists fl_github_nao_notificar_movimentador boolean not null default true;

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_status_alterado boolean not null default true;

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_tarefa_criada boolean not null default false;

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_responsavel_alterado boolean not null default false;

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_tarefa_atribuida boolean not null default false;

alter table organizacao_configuracao
    add column if not exists fl_github_ignorar_sem_responsavel boolean not null default true;

alter table organizacao_configuracao
    add column if not exists ds_github_destinatarios_modo varchar(40) not null default 'RESPONSAVEIS';

alter table organizacao_configuracao
    add column if not exists ds_github_destinatarios_extras varchar(500);

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_issue_fechada_reaberta boolean not null default false;

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_issue_label boolean not null default false;

alter table organizacao_configuracao
    add column if not exists fl_github_notificar_somente_campo_status boolean not null default false;
