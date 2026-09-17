alter table organizacao_configuracao
    add column if not exists ds_github_template_assunto_whatsapp varchar(500),
    add column if not exists ds_github_template_mensagem_whatsapp text;
