alter table organizacao_configuracao
    add column if not exists ds_github_organization_login varchar(120);

alter table organizacao_configuracao
    add column if not exists ds_github_project_v2_node_id varchar(120);

alter table organizacao_configuracao
    add column if not exists nu_github_project_v2_number integer;
