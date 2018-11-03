package com.davidrobbo.bounce.vertx.web;

import io.vertx.ext.web.Router;

public interface RouteInterceptor {

    void configure(final Router router);
}
