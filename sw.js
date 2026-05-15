// Options Analyst — Service Worker v2
const CACHE = 'options-analyst-v2';
const ASSETS = [
  './',
  './index.html',
  './manifest.json'
];

// Install: cache core assets
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE).then(cache => cache.addAll(ASSETS))
  );
  self.skipWaiting();
});

// Activate: clean old caches
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch: network-first для всего — всегда берём свежую версию с сервера
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  // API запросы — только сеть, без кэша
  if (url.hostname.includes('bybit') || url.hostname.includes('deribit') || url.hostname.includes('corsproxy')) {
    e.respondWith(
      fetch(e.request).catch(() =>
        new Response(JSON.stringify({ error: 'offline' }), {
          headers: { 'Content-Type': 'application/json' }
        })
      )
    );
    return;
  }

  // Статика — network-first: сначала сеть, fallback кеш
  // Так приложение всегда получает свежий index.html с GitHub
  e.respondWith(
    fetch(e.request)
      .then(response => {
        if (response.ok) {
          const clone = response.clone();
          caches.open(CACHE).then(c => c.put(e.request, clone));
        }
        return response;
      })
      .catch(() => caches.match(e.request))
  );
});
