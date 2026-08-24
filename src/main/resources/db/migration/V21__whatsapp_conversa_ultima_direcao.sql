alter table whatsapp_conversa
    add column if not exists tp_ultima_direcao varchar(16)
        check (tp_ultima_direcao in ('OUTBOUND', 'INBOUND'));

update whatsapp_conversa
set tp_ultima_direcao = 'INBOUND'
where tp_ultima_direcao is null;
