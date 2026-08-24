delete from whatsapp_mensagem where tp_provider = 'WHATSAPP_GATEWAY';

drop table if exists contato_notificacao cascade;
drop sequence if exists seq_contato_notificacao;

alter table organizacao_configuracao
    drop column if exists fl_exigir_consentimento,
    drop column if exists fl_consentimento_expira,
    drop column if exists nu_dias_expiracao_consentimento,
    drop column if exists fl_bloqueio_automatico,
    drop column if exists nu_limite_falhas_para_bloqueio;

alter table plano
    drop column if exists nu_limite_contatos;
