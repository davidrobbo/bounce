package com.davidrobbo.bounce.vertx.web.util;

import com.davidrobbo.bounce.guice.BounceConfigModule;
import com.davidrobbo.bounce.vertx.web.annotations.*;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.davidrobbo.bounce.vertx.web.BounceHttpException;
import com.davidrobbo.bounce.vertx.web.Pageable;
import com.davidrobbo.bounce.vertx.web.RouteInterceptor;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class Bounce {

    private static final Logger log = LoggerFactory.getLogger(Bounce.class);

    /**
     * Use to allow custom Guice Injector
     *
     * @param packagesToScan
     * @param router
     * @param injector
     * @throws Exception
     */
    public static void springify(final String[] packagesToScan, final Router router,
                                 final Injector injector) throws Exception {
        if (packagesToScan == null || packagesToScan.length == 0) {
            throw new Exception("Cannot Bounce.springify without a package list declaration!");
        } else { setup(packagesToScan, router, injector); }
    }

    /**
     * Used if No JPA Repositories need injecting (prevent Hibernate bootstrapping and save resources)
     *
     * @param packagesToScan
     * @param router
     * @throws Exception
     */
    public static void springify(final String[] packagesToScan, final Router router) throws Exception {
        if (packagesToScan == null ||packagesToScan.length == 0) {
            throw new Exception("Cannot Bounce.springify without a package list declaration!");
        } else { springify(null, packagesToScan, router, false); }
    }

    /**
     * Used if JPA Repositories need injecting
     *
     * @param vertx
     * @param packagesToScan
     * @param router
     * @throws Exception
     */
    public static void springify(final Vertx vertx, final String[] packagesToScan, final Router router) throws Exception {
        if (packagesToScan == null ||packagesToScan.length == 0) {
            throw new Exception("Cannot Bounce.springify without a package list declaration!");
        } else { springify(vertx, packagesToScan, router, true); }
    }

    private static void springify(final Vertx vertx, final String[] packagesToScan,
                                 final Router router, final Boolean enableHibernate) throws Exception {
        if (packagesToScan == null || packagesToScan.length == 0) {
            throw new Exception("Cannot Bounce.springify without a package list declaration!");
        } else if (Boolean.TRUE.equals(enableHibernate) && vertx == null) {
            throw new Exception("Enabling Hibernate/JPA Repositories requires a vertx instance");
        } else {
            final Injector injector = Boolean.TRUE.equals(enableHibernate) ?
                    Guice.createInjector(new BounceConfigModule(vertx, true)) :
                    Guice.createInjector(new BounceConfigModule());
            setup(packagesToScan, router, injector);
        }
    }

    private static void setup(final String[] packagesToScan, final Router router, final Injector injector) {
        router.route().handler(BodyHandler.create());
        router.route().order(-999999).handler(start());
        initInterceptors(packagesToScan, router, injector);
        initRequestPaths(packagesToScan, router, injector);
        router.route().last().handler(end());
    }

    private static Handler<RoutingContext> start() {
        return ctx -> {
            HttpResponse.setRouteHandled(ctx, false);
            ctx.next();
        };
    }

    private static Handler<RoutingContext> end() {
        return ctx -> {
            if (!ctx.response().ended()){
                final HttpServerResponse response = ctx.response();
                if (!Boolean.TRUE.equals(ctx.get("__route-matched"))) { response.setStatusCode(404); }
                response.end();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static void initInterceptors(final String[] toScan, final Router router, final Injector injector) {
        Stream.of(toScan)
                .map(str -> entities(str, isRouteInterceptor()))
                .flatMap(Collection::stream)
                .forEach(clazz -> initInterceptor(clazz, router, injector));
    }

    private static void initRequestPaths(final String[] toScan, final Router router, final Injector injector) {
        Stream.of(toScan)
                .map(str -> entities(str, isRestController()))
                .flatMap(Collection::stream)
                .map(clazz -> methods(clazz, isValidRequestMapping()))
                .flatMap(Collection::stream)
                .forEach(method -> initRoute(method, router, injector));
    }

    private static Set<Class> entities(final String packageName, final Predicate<Class> predicate) {

        List<ClassLoader> classLoadersList = new LinkedList<>();
        classLoadersList.add(ClasspathHelper.contextClassLoader());
        classLoadersList.add(ClasspathHelper.staticClassLoader());

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false), new ResourcesScanner())
                .setUrls(ClasspathHelper.forClassLoader(classLoadersList.toArray(new ClassLoader[0])))
                .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(packageName))));

        return reflections.getSubTypesOf(Object.class)
                .stream()
                .filter(predicate != null ? predicate : p -> true)
                .collect(toSet());
    }

    private static Set<Method> methods(final Class clazz, final Predicate<Method> predicate) {
        return Stream.of(clazz.getDeclaredMethods())
                .filter(predicate != null ? predicate : p -> true)
                .collect(toSet());
    }

    private static Predicate<Class> isRouteInterceptor() {
        return clazz -> RouteInterceptor.class.isAssignableFrom(clazz) &&
                !Modifier.isInterface(clazz.getModifiers());
    }

    private static Predicate<Class> isRestController() {
        return clazz -> clazz.isAnnotationPresent(RestController.class);
    }

    private static Predicate<Method> isValidRequestMapping() {
        return method -> method.isAnnotationPresent(RequestMapping.class);
    }

    private static <I extends RouteInterceptor> void initInterceptor(final Class<I> clazz,
                                                                     final Router router,
                                                                     final Injector injector) {
        log.info("Mapping [INTERCEPTOR] " + clazz.getSimpleName());
        injector.getInstance(clazz).configure(router);
    }

    private static void initRoute(final Method method, final Router router, final Injector injector) {
        final Class parent = method.getDeclaringClass();
        final String prefix = parent.isAnnotationPresent(RequestMapping.class) ?
                ((RequestMapping)parent.getAnnotation(RequestMapping.class)).value() : "";
        final RequestMapping annotation = method.getAnnotation(RequestMapping.class);
        log.info("Mapping [" + annotation.method().toString() + "] " + prefix + annotation.value());
        router.route(prefix + annotation.value())
                .method(annotation.method())
                .order(annotation.order())
                .handler(context -> defaultHandler(context, method, injector));
    }

    private static void defaultHandler(final RoutingContext context, final Method method, final Injector injector) {
        try {
            if (!method.getReturnType().equals(Future.class)) {
                throw new Exception("Method's annotated with @RequestMapping must return a Future");
            } else if (Boolean.TRUE.equals(context.get("__route-matched"))) {
                context.next();
            } else {
                HttpResponse.send(context, (Future) method.invoke(injector.getInstance(method.getDeclaringClass()),
                        invocationParameters(context, method)));
            }
        } catch (Exception e) { HttpResponse.send(context, Future.failedFuture(e)); }
    }

    private static Object[] invocationParameters(final RoutingContext context, final Method method) throws Exception {
        final List<Object> params = Stream.of(method.getParameters())
                .map(parameter -> invocationParameter(context, parameter))
                .collect(Collectors.toList());
        final BounceHttpException e = (BounceHttpException) params.stream()
                .filter(obj -> obj.getClass().isAssignableFrom(BounceHttpException.class))
                .findFirst()
                .orElse(null);
        if (e != null) { throw e; }
        return params.toArray();

    }

    private static Object invocationParameter(final RoutingContext context, final Parameter parameter) {
        String value = null;
        try {
            if (parameter.getType().equals(RoutingContext.class)) {
                return context;
            } else if (parameter.getType().equals(HttpServerRequest.class)) {
                return context.request();
            } else if (parameter.getType().equals(HttpServerResponse.class)) {
                return context.response();
            } else if (parameter.getType().equals(Pageable.class)) {
                final Pageable pageable = Json.decodeValue(Json.encode(toMap(context.queryParams())), Pageable.class);
                return pageable == null ? new Pageable().defaults() : pageable.defaults();
            } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                value = context.request().getParam(parameter.getAnnotation(PathVariable.class).value());
            } else if (parameter.isAnnotationPresent(RequestBody.class)) {
                value = context.getBodyAsString();
            } else if (parameter.isAnnotationPresent(RequestParam.class)) {
                final RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                final String param = requestParam.value();
                if ("".equals(param)) { value = Json.encode(toMap(context.queryParams())); }
                else {
                    if (requestParam.required() && !context.queryParams().contains(param)) {
                        throw new BounceHttpException(400, "Request Parameter " + param + " is required");
                    }
                    value = context.queryParams().get(param);
                }
            }
            return Json.decodeValue(value, parameter.getType());
        } catch (Exception e) { return e.getClass().isAssignableFrom(BounceHttpException.class) ? e : value; }
    }

    private static Map<String, Object> toMap(final MultiMap params) {
        final Map<String, Object> map =  new HashMap<>();
        params.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return map;
    }

}
