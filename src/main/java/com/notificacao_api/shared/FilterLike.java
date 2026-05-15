package com.notificacao_api.shared;


import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FilterLike {
    String path() default "";
}