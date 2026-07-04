insert into configuracao_global (
    id_configuracao_global,
    nm_plataforma,
    nu_timezone_padrao,
    fl_whatsapp_provider_padrao,
    fl_api_publica_habilitada,
    fl_templates_habilitado,
    fl_webhooks_habilitado,
    fl_telegram_habilitado,
    fl_email_habilitado,
    dt_criacao,
    dt_atualizacao
)
select
    nextval('seq_configuracao_global'),
    'Notificacao API',
    -3,
    true,
    false,
    true,
    true,
    false,
    false,
    current_timestamp,
    current_timestamp
where not exists (select 1 from configuracao_global);

select setval(
    'seq_configuracao_global',
    coalesce((select max(id_configuracao_global) from configuracao_global), 0) + 1,
    false
);
