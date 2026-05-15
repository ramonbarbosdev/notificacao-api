package com.notificacao_api.shared;

import java.time.LocalDate;

public record DateRangeFilter(
        LocalDate start,
        LocalDate end
) {
}