package com.github.davidrobbo.bounce.guice;

import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class BounceConfigModule extends AbstractModule {

    private Vertx vertx;
    private Boolean enableHibernate;

    public BounceConfigModule() {
    }

    public BounceConfigModule(final Vertx vertx, final Boolean enableHibernate) {
        this.vertx = vertx;
        this.enableHibernate = enableHibernate;
    }

    @Override
    protected void configure() {

        if (Boolean.TRUE.equals(enableHibernate)) {
            final EntityManagerFactory emf = Persistence
                    .createEntityManagerFactory("default", getHibernateProperties());
            bind(EntityManagerFactory.class).toInstance(emf);
        }
        if (vertx != null) {
            bind(Vertx.class).toInstance(vertx);
        }
    }

    private Map<String, Object> getHibernateProperties() {
        final Map<String, Object> map = new HashMap<>();
        final JsonObject config = vertx.getOrCreateContext().config();
        Assert.assertTrue("Datasource config 'datasource.driver_class' required",
                config.containsKey("datasource.driver_class"));
        Assert.assertTrue("Datasource config 'datasource.password' required",
                config.containsKey("datasource.password"));
        Assert.assertTrue("Datasource config 'datasource.url' required",
                config.containsKey("datasource.url"));
        Assert.assertTrue("Datasource config 'datasource.username' required",
                config.containsKey("datasource.username"));

        map.put("hibernate.connection.driver_class", config.getString("datasource.driver_class"));
        map.put("hibernate.connection.password", config.getString("datasource.password"));
        map.put("hibernate.connection.url", config.getString("datasource.url"));
        map.put("hibernate.connection.username", config.getString("datasource.username"));
        if (config.containsKey("hibernate.hbm2ddl.auto")) {
            map.put("hibernate.hbm2ddl.auto", config.getString("hibernate.hbm2ddl.auto"));
        }
        if (config.containsKey("datasource.show_sql")) {
            map.put("hibernate.show_sql", config.getString("datasource.show_sql"));
        }
        if (config.containsKey("hibernate.dialect")) {
            map.put("hibernate.dialect", config.getString("hibernate.dialect"));
        }
        return map;
    }
}