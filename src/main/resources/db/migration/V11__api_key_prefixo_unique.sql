create unique index if not exists ux_organizacao_api_key_prefixo_ativo
    on organizacao_api_key (ds_prefixo)
    where fl_ativo = true;
