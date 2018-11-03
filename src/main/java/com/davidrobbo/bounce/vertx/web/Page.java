package com.davidrobbo.bounce.vertx.web;

import java.util.List;

public class Page<T> {

    private List<T> content;
    private Integer page;
    private Integer size;
    private String order;
    private Long totalElements;

    public Page() {
    }

    public Page(Pageable pageable) {
        page = pageable.getPage();
        size = pageable.getSize();
        order = pageable.getOrder();
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
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

    public Long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Long totalElements) {
        this.totalElements = totalElements;
    }
}
