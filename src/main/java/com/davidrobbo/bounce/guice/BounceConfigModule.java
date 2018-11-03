package com.davidrobbo.bounce.guice;

import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

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
            final EntityManagerFactory emf = Persistence.createEntityManagerFactory("default");
            bind(EntityManagerFactory.class).toInstance(emf);
        }
        if (vertx != null) {
            bind(Vertx.class).toInstance(vertx);
        }
    }
}