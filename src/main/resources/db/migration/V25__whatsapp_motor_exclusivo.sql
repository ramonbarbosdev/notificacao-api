-- Apenas um motor WhatsApp por organizacao (Gateway OU Meta Cloud).

-- Ambos habilitados sem Meta Cloud ativa: mantem Gateway.
update organizacao_feature_flag meta
set fl_habilitado = false,
    dt_atualizacao = current_timestamp
from organizacao_feature_flag gateway
where meta.id_organizacao = gateway.id_organizacao
  and meta.ds_recurso = 'WHATSAPP_META_CLOUD'
  and meta.fl_habilitado = true
  and gateway.ds_recurso = 'WHATSAPP_GATEWAY'
  and gateway.fl_habilitado = true
  and not exists (
      select 1
      from whatsapp_configuracao wc
      where wc.id_organizacao = meta.id_organizacao
        and wc.fl_ativo = true
  );

-- Ambos habilitados com Meta Cloud ativa: mantem Meta Cloud.
update organizacao_feature_flag gateway
set fl_habilitado = false,
    dt_atualizacao = current_timestamp
from organizacao_feature_flag meta
join whatsapp_configuracao wc
  on wc.id_organizacao = meta.id_organizacao
 and wc.fl_ativo = true
where gateway.id_organizacao = meta.id_organizacao
  and gateway.ds_recurso = 'WHATSAPP_GATEWAY'
  and gateway.fl_habilitado = true
  and meta.ds_recurso = 'WHATSAPP_META_CLOUD'
  and meta.fl_habilitado = true;

-- Flag legada WHATSAPP desligada quando motores especificos existem.
update organizacao_feature_flag legado
set fl_habilitado = false,
    dt_atualizacao = current_timestamp
where legado.ds_recurso = 'WHATSAPP'
  and legado.fl_habilitado = true
  and exists (
      select 1
      from organizacao_feature_flag especifico
      where especifico.id_organizacao = legado.id_organizacao
        and especifico.ds_recurso in ('WHATSAPP_GATEWAY', 'WHATSAPP_META_CLOUD')
  );
