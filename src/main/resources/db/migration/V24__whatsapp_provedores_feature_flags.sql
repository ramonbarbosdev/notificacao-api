-- Separa recursos WhatsApp Gateway (Baileys) e WhatsApp Cloud (Meta) para planos distintos.
-- Organizacoes com WHATSAPP legado recebem ambos habilitados.

insert into organizacao_feature_flag (id_feature_flag, id_organizacao, ds_recurso, fl_habilitado, dt_criacao, dt_atualizacao)
select nextval('seq_organizacao_feature_flag'), f.id_organizacao, 'WHATSAPP_GATEWAY', f.fl_habilitado, current_timestamp, current_timestamp
from organizacao_feature_flag f
where f.ds_recurso = 'WHATSAPP'
  and not exists (
      select 1 from organizacao_feature_flag x
      where x.id_organizacao = f.id_organizacao and x.ds_recurso = 'WHATSAPP_GATEWAY'
  );

insert into organizacao_feature_flag (id_feature_flag, id_organizacao, ds_recurso, fl_habilitado, dt_criacao, dt_atualizacao)
select nextval('seq_organizacao_feature_flag'), f.id_organizacao, 'WHATSAPP_META_CLOUD', f.fl_habilitado, current_timestamp, current_timestamp
from organizacao_feature_flag f
where f.ds_recurso = 'WHATSAPP'
  and not exists (
      select 1 from organizacao_feature_flag x
      where x.id_organizacao = f.id_organizacao and x.ds_recurso = 'WHATSAPP_META_CLOUD'
  );
