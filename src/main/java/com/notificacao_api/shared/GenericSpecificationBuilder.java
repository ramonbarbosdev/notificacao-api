package com.notificacao_api.shared;


import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class GenericSpecificationBuilder {

    private GenericSpecificationBuilder() {
    }

    public static <T> Specification<T> byFilter(Object filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            for (Field field : filter.getClass().getDeclaredFields()) {
                field.setAccessible(true);

                Object value = getValue(field, filter);

                if (isEmpty(value)) {
                    continue;
                }

                FilterLike filterLike = field.getAnnotation(FilterLike.class);
                FilterEquals filterEquals = field.getAnnotation(FilterEquals.class);
                FilterPath filterPath = field.getAnnotation(FilterPath.class);
                FilterDateRange filterDateRange = field.getAnnotation(FilterDateRange.class);

                String path = resolvePath(
                        field,
                        filterLike,
                        filterEquals,
                        filterPath,
                        filterDateRange
                );

                Path<?> entityPath = getPath(root, path);

                if (filterLike != null) {
                    predicates.add(cb.like(
                            cb.lower(entityPath.as(String.class)),
                            "%" + value.toString().toLowerCase().trim() + "%"
                    ));
                    continue;
                }

                if (filterEquals != null) {
                    if (value instanceof String text) {
                        predicates.add(cb.equal(
                                cb.lower(entityPath.as(String.class)),
                                text.toLowerCase().trim()
                        ));
                    } else {
                        predicates.add(cb.equal(entityPath, value));
                    }

                    continue;
                }

                if (filterDateRange != null) {
                    DateRangeFilter range = (DateRangeFilter) value;

                    if (range.start() != null) {
                        predicates.add(cb.greaterThanOrEqualTo(
                                entityPath.as(LocalDate.class),
                                range.start()
                        ));
                    }

                    if (range.end() != null) {
                        predicates.add(cb.lessThanOrEqualTo(
                                entityPath.as(LocalDate.class),
                                range.end()
                        ));
                    }
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Object getValue(Field field, Object filter) {
        try {
            return field.get(filter);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Erro ao ler campo do filtro: " + field.getName(), e);
        }
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }

        if (value instanceof String text) {
            return text.isBlank();
        }

        if (value instanceof DateRangeFilter range) {
            return range.start() == null && range.end() == null;
        }

        return false;
    }

    private static String resolvePath(
            Field field,
            FilterLike filterLike,
            FilterEquals filterEquals,
            FilterPath filterPath,
            FilterDateRange filterDateRange
    ) {
        if (filterLike != null && !filterLike.path().isBlank()) {
            return filterLike.path();
        }

        if (filterEquals != null && !filterEquals.path().isBlank()) {
            return filterEquals.path();
        }

        if (filterDateRange != null && !filterDateRange.path().isBlank()) {
            return filterDateRange.path();
        }

        if (filterPath != null && !filterPath.value().isBlank()) {
            return filterPath.value();
        }

        return field.getName();
    }

    private static Path<?> getPath(Path<?> root, String path) {
        Path<?> current = root;

        for (String part : path.split("\\.")) {
            current = current.get(part);
        }

        return current;
    }
}
