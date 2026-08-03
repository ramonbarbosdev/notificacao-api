alter table whatsapp_sessao
    add column if not exists dt_ultima_falha timestamp;
