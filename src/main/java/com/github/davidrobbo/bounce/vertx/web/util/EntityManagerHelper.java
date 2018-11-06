package com.github.davidrobbo.bounce.vertx.web.util;

import io.vertx.core.Future;

import javax.persistence.EntityManager;

public class EntityManagerHelper {

    private EntityManager entityManager;
    private Future<Object> future;

    public EntityManagerHelper(EntityManager entityManager, Future<Object> future) {
        this.entityManager = entityManager;
        this.future = future;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Future<Object> getFuture() {
        return future;
    }

    public void setFuture(Future<Object> future) {
        this.future = future;
    }
}
