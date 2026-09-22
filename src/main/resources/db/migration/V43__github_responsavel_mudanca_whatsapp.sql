alter table organizacao_github_responsavel
    add column if not exists nu_whatsapp_anterior varchar(20);

alter table organizacao_github_responsavel
    add column if not exists dt_mudanca_whatsapp timestamp;
