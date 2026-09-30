import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../zrlog-admin-web-ui/src/main/frontend/public/service-worker.js', import.meta.url), 'utf8');
const origin = 'https://blog.example';
const asset = `${origin}/sub/admin/static/js/main.123.js`;
const page = `${origin}/sub/admin/article-edit?id=42`;
const html = () => new Response('<html>private article / original account</html>', {headers: {'Content-Type': 'text/html'}});
const script = () => new Response('/* shared build asset */', {headers: {'Content-Type': 'application/javascript'}});

function worker(urls = [asset]) {
    const listeners = new Map();
    const stores = new Map();
    const requests = [];
    let claimed = false;
    let network = script;
    const key = request => typeof request === 'string' ? request : request.url;
    const caches = {
        keys: async () => [...stores.keys()],
        delete: async name => stores.delete(name),
        open: async name => {
            if (!stores.has(name)) stores.set(name, new Map());
            const entries = stores.get(name);
            return {
                match: async request => entries.get(key(request))?.clone(),
                put: async (request, response) => entries.set(key(request), response.clone()),
            };
        },
    };
    vm.runInNewContext(source.replace('const urlsToCache = []', `const urlsToCache = ${JSON.stringify(urls)}`), {
        URL, Response, caches,
        fetch: async request => { requests.push(key(request)); return network(); },
        self: {
            location: {href: `${origin}/sub/admin/service-worker.js`},
            skipWaiting() {},
            clients: {claim: async () => { claimed = true; }},
            addEventListener: (name, callback) => listeners.set(name, callback),
        },
    });
    return {
        stores, caches, requests,
        get claimed() { return claimed; },
        set network(value) { network = value; },
        async lifecycle(name) {
            let pending;
            listeners.get(name)({waitUntil: promise => { pending = promise; }});
            await pending;
        },
        fetch(url, options = {}) {
            let response;
            listeners.get('fetch')({request: {url, method: 'GET', mode: 'cors', destination: '', ...options},
                respondWith: promise => { response = promise; }});
            return response;
        },
    };
}

test('activation removes legacy private pages before taking over existing tabs', async () => {
    const sw = worker();
    await (await sw.caches.open('my-cache-v8')).put(page, html());
    await sw.caches.open('unrelated-app');
    await sw.lifecycle('install');
    await sw.lifecycle('activate');
    assert.equal(sw.claimed, true);
    assert.equal(sw.stores.has('my-cache-v8'), false);
    assert.equal(sw.stores.has('unrelated-app'), true);
    assert.deepEqual([...sw.stores.get('my-cache-v9').keys()], [asset]);
});

test('account switches and offline requests never reuse cached HTML or API data', async () => {
    const pages = [page, `${origin}/admin/index`, `${origin}/admin/article-edit.html?id=42`,
        `${origin}/sub/admin/website/members`, `${origin}/sub/admin/user/security`,
        `${origin}/sub/api/admin/article/detail?id=42`, `${origin}/sub/admin/logout`,
        `${origin}/sub/admin/plugins/private.js`, `${origin}/sub/admin/attached/private.png`];
    const sw = worker();
    for (const url of pages) await (await sw.caches.open('my-cache-v8')).put(url, html());
    for (const network of [() => new Response('access denied', {status: 403}), () => { throw new Error('offline'); }]) {
        sw.network = network;
        for (const url of pages) {
            // Undefined means the browser must use the network, including its authorization checks.
            assert.equal(sw.fetch(url), undefined, url);
            assert.equal(sw.fetch(url, {mode: 'navigate', destination: 'document'}), undefined, url);
        }
    }
    assert.equal(sw.requests.length, 0);
});

test('unavailable assets do not leave the legacy worker and its private cache active', async () => {
    const sw = worker();
    await (await sw.caches.open('my-cache-v8')).put(page, html());
    sw.network = () => { throw new Error('asset unavailable'); };
    await sw.lifecycle('install');
    await sw.lifecycle('activate');
    assert.equal(sw.claimed, true);
    assert.equal(sw.stores.has('my-cache-v8'), false);
    assert.equal(sw.fetch(page), undefined);
});

test('precache excludes HTML even when an old server injects page URLs', async () => {
    const sw = worker([asset, page, `${origin}/sub/admin/index.html`, `${origin}/sub/admin/service-worker.js`]);
    await sw.lifecycle('install');
    assert.deepEqual(sw.requests, [asset]);
});

test('build assets remain available offline and document requests always bypass them', async () => {
    const sw = worker();
    await sw.lifecycle('install');
    sw.network = () => { throw new Error('offline'); };
    assert.equal(await (await sw.fetch(asset)).text(), '/* shared build asset */');
    assert.equal(sw.fetch(asset, {mode: 'navigate', destination: 'document'}), undefined);
    assert.equal(sw.fetch(asset, {method: 'POST'}), undefined);
});

test('login redirects, HTML, JSON, private and no-store responses never enter the asset cache', async () => {
    for (const response of [html(), new Response('{}', {headers: {'Content-Type': 'application/json'}}),
        new Response('secret', {headers: {'Content-Type': 'application/javascript', 'Cache-Control': 'private'}}),
        new Response('secret', {headers: {'Content-Type': 'application/javascript', 'Cache-Control': 'no-store'}}),
        new Response('denied', {status: 403})]) {
        const sw = worker();
        sw.network = () => response.clone();
        await sw.lifecycle('install');
        await sw.fetch(asset);
        assert.equal(sw.stores.get('my-cache-v9').size, 0);
    }
    const sw = worker();
    // Response.redirected is read-only; model the fetch response with its real headers/status.
    sw.network = () => ({status: 200, redirected: true, headers: script().headers});
    await sw.lifecycle('install');
    assert.equal(sw.stores.get('my-cache-v9').size, 0);
});
