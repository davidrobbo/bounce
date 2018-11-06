package com.github.davidrobbo.bounce.vertx;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.github.davidrobbo.bounce.guice.BounceConfigModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.github.davidrobbo.bounce.vertx.web.annotations.EnableJPARepositories;
import com.github.davidrobbo.bounce.vertx.web.annotations.EnableWeb;
import com.github.davidrobbo.bounce.vertx.web.util.Bounce;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.LoggerFactory;

public class BounceVerticle extends AbstractVerticle {

    private Injector injector;
    private HttpServer httpServer;
    private Router router;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        vertx.executeBlocking(future -> {
            try {
                final JsonObject config = vertx.getOrCreateContext().config();
                if (config.containsKey("log.level")) {
                    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                    root.setLevel(Level.valueOf(config.getString("log.level")));
                }
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
            final JsonObject config = vertx.getOrCreateContext().config();
            final Integer port = config.getInteger("server.port");
            httpServer = vertx.createHttpServer();
            router = Router.router(vertx);
            Bounce.springify(getClass().getAnnotation(EnableWeb.class).packages(), router, injector);
            httpServer.requestHandler(router::accept).listen(port != null ? port : 8080);
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
