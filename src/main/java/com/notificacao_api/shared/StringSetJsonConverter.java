package com.notificacao_api.shared;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StringSetJsonConverter implements AttributeConverter<Set<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? Set.of() : attribute);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Nao foi possivel serializar a lista de variaveis.", ex);
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashSet<>();
        }

        try {
            return new LinkedHashSet<>(OBJECT_MAPPER.readValue(dbData, STRING_LIST));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Nao foi possivel ler a lista de variaveis.", ex);
        }
    }
}
