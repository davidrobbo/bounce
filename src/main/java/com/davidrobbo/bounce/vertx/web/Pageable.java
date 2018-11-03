package com.davidrobbo.bounce.vertx.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.core.json.Json;
import org.hibernate.criterion.Order;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Pageable {

    private Integer page;
    private Integer size;
    // @todo allow for multi sort
    private String order;

    public Pageable() {
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public Order toCriteriaOrder() {
        if (order != null) {
            final String[] split = order.trim().split(",");
            if (split.length == 2) {
                return "asc".equals(split[1].toLowerCase()) ? Order.asc(split[0]) :
                        Order.desc(split[0]);
            }
        }
        return null;
    }

    public String toSqlOrder() {
        if (order != null) {
            final String[] split = order.trim().split(",");
            if (split.length == 2) {
                return " ORDER BY t." + split[0] + " " + split[1];
            }
        }
        return "";
    }

    @Override
    public String toString() {
        return Json.encodePrettily(this);
    }

    public Pageable defaults () {
        if (page == null) { page = 0; }
        if (size == null) { size = 50; }
        return this;
    }
}
