package com.notificacao_api.model;

import com.notificacao_api.enums.TipoVariavelTemplate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TemplateVariavel {

    private String chave;
    private String label;
    private TipoVariavelTemplate tipo = TipoVariavelTemplate.TEXTO;
    private Boolean obrigatoria = true;
    private String exemplo;
}
