package com.github.davidrobbo.bounce.vertx.web.annotations;

import io.vertx.core.http.HttpMethod;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {

    String value() default "/";

    HttpMethod method() default HttpMethod.GET;

    // @todo check implications of default choice
    int order() default 100;

}
