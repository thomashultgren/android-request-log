package com.walmartlabs.requestlog.lib;

public class Request {
    public Request(String method, String url) {
        mMethod = method;
        mUrl = url;
    }

    private final String mMethod;
    private final String mUrl;

    @Override
    public String toString() {
        return mMethod + ": " + mUrl;
    }

    public String getMethod() {
        return mMethod;
    }

    public String getUrl() {
        return mUrl;
    }
}