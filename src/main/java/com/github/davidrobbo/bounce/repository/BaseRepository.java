package com.github.davidrobbo.bounce.repository;

import com.google.inject.Inject;
import com.github.davidrobbo.bounce.vertx.web.util.EntityManagerHelper;
import com.github.davidrobbo.bounce.vertx.web.Page;
import com.github.davidrobbo.bounce.vertx.web.Pageable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;

import javax.persistence.*;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;


@SuppressWarnings("all")
public abstract class BaseRepository<T, ID extends Serializable> {

    @Inject private EntityManagerFactory entityManagerFactory;
    @Inject private Vertx vertx;

    public BaseRepository() {}

    //@todo order and count are VERY HACKY!
    public Future<Page<T>> findAll(final Pageable pageable, final String sql, final Map<String, Object> params) {
        return noTransaction(handler -> {
            try {
                final Page<T> page = new Page<>(pageable);
                final Query query = handler.getEntityManager().createQuery(sql + pageable.toSqlOrder());
                params.forEach(query::setParameter);
                query.setFirstResult(pageable.getSize() * pageable.getPage());
                query.setMaxResults(pageable.getSize());
                page.setContent(query.getResultList());
                final Query countQuery = handler.getEntityManager().createQuery(countSql(sql));
                params.forEach(countQuery::setParameter);
                page.setTotalElements((Long)((Object[])countQuery.getSingleResult())[0]);
                handler.getFuture().complete(page);
            } catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    public Future<List<T>> findAll(final String sql, final Map<String, Object> params) {
        return noTransaction(handler -> {
            try {
                final Query query = handler.getEntityManager().createQuery(sql);
                params.forEach(query::setParameter);
                handler.getFuture().complete(query.getResultList());
            } catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    public Future<Page<T>> findAll(final Pageable pageable) {
        return noTransaction(handler -> {
            try {
                final Page<T> page = new Page(pageable);
                final Class genericType = getGenericTypeClass();
                final Session session = (Session) handler.getEntityManager().getDelegate();
                final Criteria criteria = session.createCriteria(genericType);
                criteria.setFirstResult(pageable.getPage() * pageable.getSize());
                criteria.setMaxResults(pageable.getSize());
                final Order order = pageable.toCriteriaOrder();
                if (order != null) { criteria.addOrder(order); }
                final List<T> pageContent = criteria.list();
                page.setContent(criteria.list());
                final Criteria criteriaCount = session.createCriteria(genericType);
                criteriaCount.setProjection(Projections.rowCount());
                page.setTotalElements((Long) criteriaCount.uniqueResult());
                handler.getFuture().complete(page);
            } catch (Exception e) {
                handler.getFuture().fail(e);
            }
        });
    }

    public Future<List<T>> findAll() {
        return noTransaction(handler -> {
            try {
                handler.getFuture().complete(handler.getEntityManager().createQuery("SELECT t FROM " +
                        table() + " t").getResultList());
            } catch (Exception e) {
                handler.getFuture().fail(e);
            }
        });
    }

    public Future<T> findOne(final ID id) {
        return noTransaction(handler -> {
            try {
                handler.getFuture().complete(
                        handler.getEntityManager().createQuery("SELECT t FROM " + table() + " t WHERE id = :id")
                            .setParameter("id", id)
                            .getSingleResult()
                );
            } catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    public Future<T> findOne(final T t) {
        return noTransaction(handler -> {
            try { handler.getFuture().complete(handler.getEntityManager().find(t.getClass(), getId(t))); }
            catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    public Future<Void> delete(final T t) {

        return performTransaction(handler -> {
            try {
                final T found = (T) handler.getEntityManager().find(t.getClass(), getId(t));
                handler.getEntityManager().remove(found);
                handler.getFuture().complete();
            } catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    public Future<T> save(final T t) {

        return performTransaction(handler -> {
            try {
                handler.getEntityManager().persist(t);
                handler.getFuture().complete(t);
            } catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    public Future<List<T>> saveAll(final List<T> ts) {

        return performTransaction(handler -> {
            try {
                if (ts != null || ts.size() > 0) {
                    for (T t : ts) { handler.getEntityManager().persist(t); }
                }
                handler.getFuture().complete(ts);
            } catch (Exception e) { handler.getFuture().fail(e); }
        });
    }

    protected Future noTransaction(final Handler<EntityManagerHelper> actionHandler) {
        final Future future = Future.future();
        final EntityManager entityManager = entityManagerFactory.createEntityManager();
        vertx.executeBlocking(f1 -> {
            try { actionHandler.handle(new EntityManagerHelper(entityManager, f1)); }
            catch (Exception e) { f1.fail(e); }
        }, done -> {
            if (done.succeeded()) { future.complete(done.result()); }
            else { future.fail(done.cause()); }
            entityManager.close();
        });
        return future;
    }

    protected Future performTransaction(final Handler<EntityManagerHelper> actionHandler) {
        final Future future = Future.future();
        final EntityManager entityManager = entityManagerFactory.createEntityManager();
        vertx.executeBlocking(f1 -> {
            try {
                entityManager.getTransaction().begin();
                actionHandler.handle(new EntityManagerHelper(entityManager, f1));
            } catch (Exception e) { f1.fail(e); }
        }, done -> {
            if (done.succeeded()) {
                entityManager.getTransaction().commit();
                future.complete(done.result());
            } else {
                entityManager.getTransaction().rollback();
                future.fail(done.cause());
            }
            entityManager.close();
        });
        return future;
    }

    private ID getId(final T t) throws Exception {
        final Field field = idField(t.getClass());
        field.setAccessible(true);
        return (ID) field.get(t);
    }

    private Field idField(final Class clazz) throws Exception {
        final Optional<Field> id =  Stream.of(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findFirst();
        return id.isPresent() ? id.get() : idField(clazz.getSuperclass());
    }

    private Class<T> getGenericTypeClass() {
        try {
            String className = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0].getTypeName();
            Class<?> clazz = Class.forName(className);
            return (Class<T>) clazz;
        } catch (Exception e) {
            throw new IllegalStateException("Class is not parametrized with generic type!!! Please use extends <> ");
        }
    }

    private String table() throws Exception {
        return ((Table)getGenericTypeClass().getAnnotation(Table.class)).name();
    }

    private String countSql(final String sql) {
        return sql.substring(0, 7) + "COUNT(*), " + sql.substring(7);
    }
}
