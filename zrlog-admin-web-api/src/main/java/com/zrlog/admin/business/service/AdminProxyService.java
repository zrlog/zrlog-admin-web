package com.zrlog.admin.business.service;

import com.zrlog.util.NetworkProxy;

import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AdminProxyService {
    @Inject
    public AdminProxyService() {}

    public boolean testConnection(NetworkProxy.Settings settings) {
        HttpClient client = HttpClient.newBuilder()
                .proxy(settings.proxySelector())
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.zrlog.com/"))
                .timeout(Duration.ofSeconds(10)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        try {
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 400;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException error) {
            return false;
        }
    }
}
