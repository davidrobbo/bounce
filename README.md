# bounce
Spring like annotation driven components for Vertx (RequestMapping, PathVariable, RequestParam, RestController) with optional Hibernate implementation (EnableJPARespositories) utilising Guice for Injection

```
// Create a verticle that extends BounceVerticle -
// use the @EnableWeb annotation to identify
// web packages to scan

package com.davidrobbo.bounce.vertx.web.annotations;

import com.davidrobbo.bounce.vertx.BounceVerticle;

@EnableWeb(packages = {"com.foobar"})
public class FooVerticle extends BounceVerticle {}

```

```
// Use annotations to define your Controller classes
// and API endpoints with the annotations shown
// below.

package com.foobar;

import com.davidrobbo.bounce.vertx.web.Pageable;
import com.davidrobbo.bounce.vertx.web.annotations.*;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

@RestController
@RequestMapping("/foo")
public class FooController {

    @RequestMapping(value = "/bar/:id", order = 101, method = HttpMethod.POST)
    public Future<String> testMethod(RoutingContext ctx, HttpServerRequest req,
                                     HttpServerResponse res, Pageable pageable,
                                     @PathVariable("id") final Integer id,
                                     @RequestBody final Foo foo,
                                     @RequestParam Map<String, Object> allParams,
                                     @RequestParam(value = "foobar", required = false) final String foobar) {
        return Future.succeededFuture("Hello world");
    }
}

```