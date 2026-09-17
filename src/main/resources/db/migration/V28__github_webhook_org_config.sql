alter table organizacao_configuracao
    add column if not exists ds_github_status_disparo varchar(500);
    
alter table organizacao_configuracao
    add column if not exists ds_github_frase_ativacao_whatsapp varchar(500);
