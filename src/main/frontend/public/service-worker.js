const CACHE_NAME = "my-cache-v7";
const urlsToCache = [];
const networkOnlyPathMatchers = [
    (pathname) => pathname.includes("/api/"),
    (pathname) => pathname.includes("/admin/logout"),
    (pathname) => pathname.includes("/admin/plugins/"),
    (pathname) => pathname.includes("/admin/attached"),
    (pathname) => pathname.includes("/admin/template/preview-image"),
];

const shouldUseNetworkOnly = (pathname) => networkOnlyPathMatchers.some((matcher) => matcher(pathname));

self.addEventListener("install", (event) => {
    self.skipWaiting();
    event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(urlsToCache)));
});

self.addEventListener("activate", (event) => {
    const cacheWhitelist = [CACHE_NAME];
    event.waitUntil(
        caches
            .keys()
            .then((cacheNames) => {
                return Promise.all(
                    cacheNames.map((cacheName) => {
                        if (cacheWhitelist.indexOf(cacheName) === -1) {
                            return caches.delete(cacheName);
                        }
                    })
                );
            })
            .then(() => {
                return self.clients.claim();
            })
    );
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    const requestUrl = new URL(request.url);
    const pathname = requestUrl.pathname;
    // 跳过不支持的协议
    if (!request.url.startsWith("http")) {
        return;
    }
    if (request.method !== "GET") {
        return;
    }
    if (shouldUseNetworkOnly(pathname)) {
        return;
    }

    event.respondWith(
        caches.open(CACHE_NAME).then(async (cache) => {
            const cachedResponse = await cache.match(request);

            // 触发后台更新
            const fetchPromise = fetch(request, {
                redirect: "follow",
            })
                .then((networkResponse) => {
                    // 只有有效响应才更新缓存
                    if (networkResponse && networkResponse.status === 200) {
                        cache.put(request, networkResponse.clone());
                    }
                    return new Response(networkResponse.body, {
                        status: networkResponse.status,
                        statusText: networkResponse.statusText,
                        headers: networkResponse.headers,
                    });
                })
                .catch(() => {
                    // 网络失败也不抛错
                    return null;
                });

            // 返回缓存内容（如果有），否则等网络返回

            if (cachedResponse) {
                // 当从缓存读取到响应并准备返回给浏览器时
                return new Response(cachedResponse.body, {
                    status: cachedResponse.status,
                    statusText: cachedResponse.statusText,
                    headers: cachedResponse.headers,
                });
            }
            return fetchPromise
                .then(async (response) => {
                    if (response) {
                        return response;
                    }
                    return caches.match("/admin/offline");
                })
                .then((response) => {
                    if (response) {
                        return new Response(response.body, {
                            status: response.status,
                            statusText: response.statusText,
                            headers: response.headers,
                        });
                    }
                });
        })
    );
});
