create unique index if not exists ux_whatsapp_mensagem_org_id_externo
    on whatsapp_mensagem(id_organizacao, ds_id_externo)
    where ds_id_externo is not null;

create index if not exists ix_whatsapp_mensagem_org_telefone_envio
    on whatsapp_mensagem(id_organizacao, ds_telefone, dt_envio desc nulls last);
