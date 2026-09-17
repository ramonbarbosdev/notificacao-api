alter table organizacao_configuracao
    add column if not exists fl_webhook_registrar_fila_sem_destinatario boolean not null default false;

comment on column organizacao_configuracao.fl_webhook_registrar_fila_sem_destinatario is
    'Quando true, GitHub/webhook generico sem responsavel ou destinatario geram registro bloqueado na fila.';
