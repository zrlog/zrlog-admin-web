const CACHE_NAME = "my-cache-v9";
const urlsToCache = [];
// The server injects build assets only. Never cache navigations or account data,
// including HTML fetched through a client router rather than a navigation.
const staticUrls = new Set(urlsToCache.map((url) => new URL(url, self.location.href).href).filter((url) => {
    const pathname = new URL(url).pathname;
    return /\.(?:js|css|woff2?|ttf|otf|png|jpe?g|gif|svg|ico|webp)$/i.test(pathname)
        && !pathname.endsWith("/service-worker.js");
}));

const canCache = (response) => response && response.status === 200 && !response.redirected
    && !/\b(?:private|no-store)\b/i.test(response.headers.get("Cache-Control") || "")
    && /^(?:text\/css|(?:text|application)\/javascript|application\/x-javascript|image\/|font\/|application\/(?:font-|x-font-|vnd\.ms-fontobject))/i
        .test(response.headers.get("Content-Type") || "");

self.addEventListener("install", (event) => {
    self.skipWaiting();
    event.waitUntil(caches.open(CACHE_NAME).then((cache) => Promise.all([...staticUrls].map(async (url) => {
        try {
            const response = await fetch(url);
            if (canCache(response)) await cache.put(url, response);
        } catch {
            // An unavailable asset must not prevent activation and legacy cache cleanup.
        }
    }))));
});

self.addEventListener("activate", (event) => {
    event.waitUntil(caches.keys().then((names) => Promise.all(
        // Remove legacy caches containing authenticated pages before claiming tabs.
        names.filter((name) => /^my-cache-v\d+$/.test(name) && name !== CACHE_NAME)
            .map((name) => caches.delete(name))
    )).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.method !== "GET" || request.mode === "navigate" || request.destination === "document"
        || !staticUrls.has(request.url)) {
        return;
    }
    event.respondWith(caches.open(CACHE_NAME).then(async (cache) => {
        const cached = await cache.match(request);
        if (cached && canCache(cached)) return cached;
        const response = await fetch(request);
        if (canCache(response)) await cache.put(request, response.clone());
        return response;
    }));
});
