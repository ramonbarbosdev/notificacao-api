package com.notificacao_api.shared;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificacao_api.model.TemplateVariavel;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TemplateVariavelListJsonConverter implements AttributeConverter<List<TemplateVariavel>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<TemplateVariavel>> TEMPLATE_VARIAVEL_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<TemplateVariavel> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Nao foi possivel serializar as variaveis do template.", ex);
        }
    }

    @Override
    public List<TemplateVariavel> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return new ArrayList<>(OBJECT_MAPPER.readValue(dbData, TEMPLATE_VARIAVEL_LIST));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Nao foi possivel ler as variaveis do template.", ex);
        }
    }
}
