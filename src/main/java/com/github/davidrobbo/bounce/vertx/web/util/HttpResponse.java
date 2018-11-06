package com.github.davidrobbo.bounce.vertx.web.util;

import com.github.davidrobbo.bounce.vertx.web.BounceHttpResponse;
import com.github.davidrobbo.bounce.vertx.web.BounceHttpException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;

public class HttpResponse {

    private static void send(final RoutingContext context, final int statusCode, final Object body) {
        context.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(statusCode);
        if (body != null) {
            context.response()
                    .setChunked(true)
                    .write(Json.encode(body));
        }
        setRouteHandled(context, true);
        context.next();
    }

    public static void setRouteHandled(final RoutingContext context, final Boolean matched) {
        context.put("__route-matched", matched);
    }

    @SuppressWarnings("all")
    public static void send(final RoutingContext context, final Future future) {
        future.setHandler((Handler<AsyncResult>) handler -> {
            if (handler.succeeded()) {
                final Object rawResult = handler.result();
                if (rawResult.getClass().isAssignableFrom(BounceHttpResponse.class)) {
                    send(context, ((BounceHttpResponse) rawResult).getStatusCode(),
                            ((BounceHttpResponse) rawResult).getBody());
                } else if (rawResult.getClass().isAssignableFrom(Void.class) && !context.response().ended()) {
                    context.next();
                } else { send(context, 200, rawResult); }
            } else {
                final Throwable cause = handler.cause();
                if (cause != null && cause.getClass().isAssignableFrom(BounceHttpException.class)) {
                    final BounceHttpException bounceHttpException = (BounceHttpException) cause;
                    send(context, bounceHttpException.getStatusCode(),
                            Collections.singletonMap("message", bounceHttpException.getStatusMessage()));
                } else {
                    send(context, 500, Collections.singletonMap("message", "An Error Occurred"));
                }
            }
        });
    }
}
