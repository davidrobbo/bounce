package com.github.davidrobbo.bounce.vertx;

import com.github.davidrobbo.bounce.repository.BaseRepository;
import com.github.davidrobbo.bounce.vertx.web.*;
import com.github.davidrobbo.bounce.vertx.web.annotations.*;
import com.google.inject.Inject;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.persistence.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class BounceVerticleTest {

    private static int httpPort = 8080;

    @ClassRule
    public static final RunTestOnContext rule = new RunTestOnContext();

    @BeforeClass
    public static void setup(final TestContext context) throws Exception {
        final JsonObject config = rule.vertx().fileSystem()
                .readFileBlocking("application.properties.json").toJsonObject();
        final Integer port = config.getInteger("server.port");
        if (port != null) { httpPort = port; }
        rule.vertx().deployVerticle(new TestVerticle(), new DeploymentOptions().setConfig(config),
                context.asyncAssertSuccess());

    }

    @AfterClass
    public static void tearDown(final TestContext context) {

        rule.vertx().close(context.asyncAssertSuccess());
    }

    @Entity(name = "Bar")
    @Table(name = "Bar")
    public static class Bar {

        @Id
        @GeneratedValue(generator = "increment")
        @GenericGenerator(name = "increment", strategy = "increment")
        @Column(name = "id")
        private Integer id;
        @Column(name = "name")
        private String name;

        public Bar() {
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return Json.encode(this);
        }
    }

    @Entity(name = "Foo")
    @Table(name = "Foo")
    public static class Foo {

        @Id
        @GeneratedValue(generator = "increment")
        @GenericGenerator(name = "increment", strategy = "increment")
        @Column(name = "id")
        private Integer id;
        @Column(name = "name")
        private String name;
        @OneToOne(cascade = CascadeType.ALL)
        @JoinColumn(name = "bar", columnDefinition = "integer")
        @NotFound(action = NotFoundAction.IGNORE)
        private Bar bar;

        public Foo() {
        }

        public Foo(Integer id) { this.id = id; }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Bar getBar() {
            return bar;
        }

        public void setBar(Bar bar) {
            this.bar = bar;
        }

        @Override
        public String toString() {
            return Json.encode(this);
        }
    }

    public static class FooRepository extends BaseRepository<Foo, Integer> {
        public FooRepository() {}

        public Future<List<Foo>> findByName(final String name) {
            return findAll("SELECT t FROM Foo t where name = :name",
                    Collections.singletonMap("name", name));
        }
    }

    @EnableWeb(packages = {"com.github.davidrobbo.bounce.vertx"})
    @EnableJPARepositories
    private static class TestVerticle extends BounceVerticle {
        @Override
        public void start(Future<Void> startFuture) throws Exception {
            super.start(startFuture);
        }

        @Override
        public void stop(Future<Void> stopFuture) throws Exception {
            super.stop(stopFuture);
        }
    }

    @RestController
    public static class TestController {

        private final FooRepository fooRepository;

        @Inject
        public TestController(final FooRepository fooRepository) {
            this.fooRepository = fooRepository;
        }

        @RequestMapping
        public Future<Map<String, String>> testMethod() {
            return Future.succeededFuture(Collections.singletonMap("message", "HELLO WORLD"));
        }

        @RequestMapping(value = "/unauthorized")
        public Future<Map<String, String>> testMethodNotReachable() {
            return Future.succeededFuture(Collections.singletonMap("message", "HELLO WORLD"));
        }

        @RequestMapping(value = "/should/fail", order = 0)
        public Future<String> testFailParam(@RequestParam(value = "test") String param) {
            return Future.succeededFuture("TEST");
        }

        @RequestMapping(method = HttpMethod.POST)
        public Future<List<Foo>> save(@RequestBody final Foo foo) {
            return fooRepository.saveAll(Collections.singletonList(foo));
        }

        @RequestMapping(value = "/all", order = 1)
        public Future<List<Foo>> findAll() {
            return fooRepository.findAll();
        }

        @RequestMapping(value = "/:id", order = 2)
        public Future findOne(@PathVariable("id") final Integer id) {
            final Future future = Future.future();
            try {
                final Future<Foo> byId = fooRepository.findOne(id);
                final Future<Foo> byT = fooRepository.findOne(new Foo(id));
                CompositeFuture.all(byId, byT).setHandler(compositeFutureAsyncResult -> {
                    if (compositeFutureAsyncResult.succeeded()) {
                        future.complete(byT.result());
                    } else {
                        compositeFutureAsyncResult.cause().printStackTrace();
                        future.fail(compositeFutureAsyncResult.cause()); }
                });
            } catch (Exception e) { future.fail(e); }
            return future;
        }

        @RequestMapping(value = "/by/name", order = 2)
        public Future<List<Foo>> byName(@RequestParam final Map<String, Object> params) {
            Assert.assertNotNull(params);
            Assert.assertTrue(params.containsKey("name"));
            return fooRepository.findByName((String) params.get("name"));
        }

        @RequestMapping(value = "/by/page", order = 2)
        public Future<Page<Foo>> byName(final Pageable pageable) {
            Assert.assertNotNull(pageable);
            Assert.assertTrue(1 == pageable.getSize());
            Assert.assertTrue(0 == pageable.getPage());
            return fooRepository.findAll(pageable);
        }

        @RequestMapping(value = "/:id", order = 2, method = HttpMethod.DELETE)
        public Future byName(@PathVariable("id") final Integer id) {
            final Future future = Future.future();
            try {
                fooRepository.delete(new Foo(id)).setHandler(deleted -> {
                    if (deleted.succeeded()) {
                        future.complete(new BounceHttpResponse(204));
                    } else {
                        future.fail(new BounceHttpException(500, "Unable to delete"));
                    }
                });
            } catch (Exception e) { future.fail(e);}
            return future;
        }
    }

    @RestController
    @RequestMapping("/prefix")
    public static class TestTwoController {

        public TestTwoController() {
        }

        @RequestMapping(value = "/suffix")
        public Future<Map<String, String>> testMethod() {
            return Future.succeededFuture(Collections.singletonMap("message", "HELLO WORLD 2"));
        }
    }

    public static class UnAuthInterceptor implements RouteInterceptor {

        @Override
        public void configure(final Router router) {
            router.route("/unauthorized").order(-1).handler(ctx -> ctx.response().setStatusCode(401).end());
        }
    }

    @Test
    public void testNotFound(final TestContext context) throws Exception {

        final Async async = context.async();
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/a/not-found", response -> {

            context.assertTrue(response.statusCode() == 404);
            async.complete();
        });
    }

    @Test
    public void testRestController(final TestContext context) throws Exception {

        final Async async = context.async();
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {

                context.assertEquals("HELLO WORLD", body.toJsonObject().getString("message"));
                async.complete();
            });
        });
    }

    @Test
    public void testRouteInterceptor(final TestContext context) throws Exception {

        final Async async = context.async();
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/unauthorized", response -> {

            context.assertTrue(response.statusCode() == 401);
            async.complete();
        });
    }

    @Test
    public void testFailOnRequestParam(final TestContext context) throws Exception {

        final Async async = context.async();
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/should/fail", response -> {

            context.assertTrue(response.statusCode() == 400);
            async.complete();
        });
    }

    @Test
    public void testClassLevelRequestMapping(final TestContext context) throws Exception {

        final Async async = context.async();
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/prefix/suffix", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {

                context.assertEquals("HELLO WORLD 2", body.toJsonObject().getString("message"));
                async.complete();
            });
        });
    }

    @Test
    public void testJPA(final TestContext context) throws Exception {

        final Async async = context.async();
        final HttpClient httpClient = rule.vertx().createHttpClient();
        final Foo foo = new Foo();
        final Bar bar = new Bar();
        foo.setName("Foo");
        bar.setName("Bar");
        foo.setBar(bar);
        httpClient.post(httpPort, "localhost", "/", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {

                try {
                    isFoos(context, body);
                    getById(context, async);
                } catch (Exception e) {
                    context.fail(e);
                    async.complete();
                }
            });
        }).setChunked(true).write(Json.encode(foo)).end();
    }

    public void getById(final TestContext context, final Async async) throws Exception {
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/1", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {
                try {
                    isFoo(context, body.toString());
                    getAll(context, async);
                } catch (Exception e) {
                    context.fail(e);
                    async.complete();
                }
            });
        });
    }

    public void getAll(final TestContext context, final Async async) throws Exception {
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/all", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {
                try {
                    isFoos(context, body);
                    byName(context, async);
                } catch (Exception e) {
                    context.fail(e);
                }
            });
        });
    }

    public void byName(final TestContext context, final Async async) throws Exception {
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/by/name?name=Foo", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {
                try {
                    isFoos(context, body);
                    byPage(context, async);
                } catch (Exception e) {
                    context.fail(e);
                }
            });
        });
    }

    public void byPage(final TestContext context, final Async async) throws Exception {
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.getNow(httpPort, "localhost", "/by/page?page=0&size=1", response -> {

            context.assertTrue(response.statusCode() == 200);
            response.bodyHandler(body -> {
                try {
                    final Page foos = Json.decodeValue(body, Page.class);
                    final JsonArray array = new JsonArray();
                    foos.getContent().forEach(array::add);
                    context.assertTrue(1 == foos.getSize());
                    isFoos(context, array.toBuffer());
                    delete(context, async);
                } catch (Exception e) {
                    context.fail(e);
                }
            });
        });
    }

    private void delete(final TestContext context, final Async async) {
        final HttpClient httpClient = rule.vertx().createHttpClient();
        httpClient.delete(httpPort, "localhost", "/1", response -> {
            context.assertTrue(response.statusCode() == 204);
            async.complete();
        }).end();
    }

    private void isFoo(final TestContext context, final String body) {
        final Foo savedFoo = Json.decodeValue(body, Foo.class);
        context.assertEquals("Foo", savedFoo.getName());
        context.assertNotNull(savedFoo.getBar());
        context.assertEquals("Bar", savedFoo.getBar().getName());
    }

    private void isFoos(final TestContext context, final Buffer body) {
        final JsonArray savedFoos = body.toJsonArray();
        context.assertEquals(1, savedFoos.size());
        isFoo(context, savedFoos.getJsonObject(0).toString());
    }

}