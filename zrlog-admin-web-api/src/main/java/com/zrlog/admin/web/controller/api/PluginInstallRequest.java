package com.zrlog.admin.web.controller.api;

import com.hibegin.common.io.handler.ReadWriteSelectorHandler;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.web.session.HttpSession;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/** Adapts the legacy multipart route to a resource-address installation request. */
final class PluginInstallRequest implements HttpRequest {
    private final HttpRequest original;
    private final Map<String, String[]> form;
    private final byte[] body;

    PluginInstallRequest(HttpRequest original, Map<String, String[]> form, String encodedBody) {
        this.original = original;
        this.form = form;
        this.body = encodedBody == null ? null : encodedBody.getBytes(StandardCharsets.UTF_8);
    }

    @Override public String getHeader(String key) {
        if (body != null && "Content-Type".equalsIgnoreCase(key)) return "application/x-www-form-urlencoded;charset=UTF-8";
        if (body != null && "Content-Length".equalsIgnoreCase(key)) return Integer.toString(body.length);
        if ("Cookie".equalsIgnoreCase(key) && original.getHeader("X-ZrLog-Admin-Token") != null) {
            // The base proxy forwards Cookie/Authorization. Preserve the authenticated admin-header credential.
            return "admin-token=" + original.getHeader("X-ZrLog-Admin-Token");
        }
        return original.getHeader(key);
    }
    @Override public Map<String, String> getHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>(original.getHeaderMap());
        headers.keySet().removeIf(key -> key.equalsIgnoreCase("Content-Type") || key.equalsIgnoreCase("Cookie") || key.equalsIgnoreCase("Content-Length"));
        if (getHeader("Content-Type") != null) headers.put("Content-Type", getHeader("Content-Type"));
        if (getHeader("Cookie") != null) headers.put("Cookie", getHeader("Cookie"));
        if (getHeader("Content-Length") != null) headers.put("Content-Length", getHeader("Content-Length"));
        return headers;
    }
    @Override public InputStream getInputStream() { return body == null ? original.getInputStream() : new ByteArrayInputStream(body); }
    @Override public ByteBuffer getRequestBodyByteBuffer() { return body == null ? original.getRequestBodyByteBuffer() : ByteBuffer.wrap(body); }
    @Override public ByteBuffer getRequestBodyByteBuffer(int offset) {
        return body == null ? original.getRequestBodyByteBuffer(offset) : ByteBuffer.wrap(body, offset, body.length - offset).slice();
    }
    @Override public String getQueryStr() { return body == null ? original.getQueryStr() : ""; }
    @Override public Map<String, String[]> getParamMap() { return form == null ? original.getParamMap() : form; }
    @Override public Map<String, String[]> decodeParamMap() { return form == null ? original.decodeParamMap() : form; }
    @Override public String getParaToStr(String key) {
        if (form == null) return original.getParaToStr(key);
        String[] values = form.get(key);
        return values == null || values.length == 0 ? null : values[0];
    }
    @Override public String getRemoteHost() { return original.getRemoteHost(); }
    @Override public String getUri() { return original.getUri(); }
    @Override public String getUrl() { return original.getUrl(); }
    @Override public String getFullUrl() { return original.getFullUrl(); }
    @Override public String getRealPath() { return original.getRealPath(); }
    @Override public HttpMethod getMethod() { return original.getMethod(); }
    @Override public Cookie[] getCookies() { return original.getCookies(); }
    @Override public HttpSession getSession() { return original.getSession(); }
    @Override public Boolean getParaToBool(String key) { return original.getParaToBool(key); }
    @Override public Integer getParaToInt(String key) { return original.getParaToInt(key); }
    @Override public File getFile(String key) { return original.getFile(key); }
    @Override public Map<String, Object> getAttr() { return original.getAttr(); }
    @Override public String getScheme() { return original.getScheme(); }
    @Override public RequestConfig getRequestConfig() { return original.getRequestConfig(); }
    @Override public ReadWriteSelectorHandler getHandler() { return original.getHandler(); }
    @Override public long getCreateTime() { return original.getCreateTime(); }
    @Override public ByteBuffer getInputByteBuffer() { return original.getInputByteBuffer(); }
    @Override public ServerConfig getServerConfig() { return original.getServerConfig(); }
    @Override public ApplicationContext getApplicationContext() { return original.getApplicationContext(); }
    @Override public HttpVersion getHttpVersion() { return original.getHttpVersion(); }
    @Override public String getContextPath() { return original.getContextPath(); }
}
