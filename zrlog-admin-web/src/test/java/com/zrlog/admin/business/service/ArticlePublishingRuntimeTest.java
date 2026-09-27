package com.zrlog.admin.business.service;

import org.junit.Test;

import java.net.URLClassLoader;
import java.net.URL;
import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ArticlePublishingRuntimeTest {
    @Test
    public void ordinaryJdkCanPrepareArticlesWithoutPolyglotOnClasspath() throws Exception {
        URL[] classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(path -> !path.contains("zrlog-polyglot-template") && !path.contains("/org/graalvm/"))
                .map(path -> {
                    try { return new File(path).toURI().toURL(); }
                    catch (Exception e) { throw new IllegalArgumentException(e); }
                }).toArray(URL[]::new);
        try (URLClassLoader loader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.zrlog.blog.polyglot.markdown.MarkdownJsRenderer"));
            Class<?> service = loader.loadClass(ArticlePublishingService.class.getName());
            var constructor = service.getDeclaredConstructor(loader.loadClass(AdminArticleService.class.getName()));
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(new Object[]{null});
            Class<?> request = loader.loadClass("com.zrlog.admin.business.rest.request.CreateArticleRequest");
            Object body = request.getConstructor().newInstance();
            request.getMethod("setEditorType", String.class).invoke(body, "markdown");
            request.getMethod("setMarkdown", String.class).invoke(body, "# Title");
            service.getMethod("prepareRequest", request).invoke(instance, body);
            assertNull(request.getMethod("getContent").invoke(body));
            request.getMethod("setContent", String.class).invoke(body, "<h1>Title</h1>");
            service.getMethod("prepareRequest", request).invoke(instance, body);
            assertEquals("<h1>Title</h1>", request.getMethod("getContent").invoke(body));
        }
    }
}
