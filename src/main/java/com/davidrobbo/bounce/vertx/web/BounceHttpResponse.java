package com.davidrobbo.bounce.vertx.web;

public class BounceHttpResponse {

    private int statusCode;
    private Object body;

    public BounceHttpResponse() {
    }

    public BounceHttpResponse(int statusCode) {
        this.statusCode = statusCode;
    }

    public BounceHttpResponse(int statusCode, Object body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
