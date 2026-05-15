package com.notificacao_api.shared;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterDateRange {
    String path() default "";
}