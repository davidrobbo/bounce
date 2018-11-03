package com.davidrobbo.bounce.vertx;

import com.davidrobbo.bounce.guice.BounceConfigModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.davidrobbo.bounce.vertx.web.annotations.EnableJPARepositories;
import com.davidrobbo.bounce.vertx.web.annotations.EnableWeb;
import com.davidrobbo.bounce.vertx.web.util.Bounce;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class BounceVerticle extends AbstractVerticle {

    private Injector injector;
    private HttpServer httpServer;
    private Router router;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        vertx.executeBlocking(future -> {
            try {
                final boolean enableHibernate = getClass().isAnnotationPresent(EnableJPARepositories.class);
                injector = Guice.createInjector(new BounceConfigModule(vertx, enableHibernate));
                future.complete();
            } catch (Exception e) { future.fail(e); }
        }, asyncResult -> {
            if (asyncResult.succeeded()) { init(startFuture); }
            else { startFuture.fail(asyncResult.cause()); }
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        stopFuture.complete();
    }

    private void init(final Future<Void> startFuture) {
        if (getClass().isAnnotationPresent(EnableWeb.class)) {
            vertx.executeBlocking(future -> {
                try {
                    initWeb(startFuture);
                    future.complete();
                } catch (Exception e) { future.fail(e); }
            }, asyncResult -> { if (asyncResult.failed()) { startFuture.fail(asyncResult.cause()); } });
        } else { startFuture.complete(); }
    }

    private void initWeb(final Future<Void> startFuture) {
        try {
            httpServer = vertx.createHttpServer();
            router = Router.router(vertx);
            Bounce.springify(getClass().getAnnotation(EnableWeb.class).packages(), router, injector);
            // @todo config entry
            httpServer.requestHandler(router::accept).listen(8080);
            startFuture.complete();
        } catch (Exception e) { startFuture.fail(e); }
    }

    public Injector getInjector() {
        return injector;
    }

    public HttpServer getHttpServer() {
        return httpServer;
    }

    public Router getRouter() {
        return router;
    }
}
