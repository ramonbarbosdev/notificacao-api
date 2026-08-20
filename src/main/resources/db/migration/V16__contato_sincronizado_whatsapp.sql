alter table public.contato_notificacao
    add column if not exists fl_sincronizado_whatsapp boolean not null default false;

create index if not exists ix_contato_notificacao_sincronizado_whatsapp
    on public.contato_notificacao(id_organizacao, tp_canal, fl_sincronizado_whatsapp);
