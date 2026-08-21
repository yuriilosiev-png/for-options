// OPTIONS ANALYZER — Service Worker v5
// v4.1 FIX: checkAlertsBackground() учитывает alert.exchange (Bybit/Deribit)
//
// v5 FIX: страница не обновлялась после заливки нового index.html.
// Стратегия здесь и раньше была network-first, но fetch() внутри SW уважает
// обычный HTTP-кэш, а GitHub Pages отдаёт index.html с max-age=600. Плюс в APK
// стоит WebSettings.LOAD_DEFAULT. В итоге WebView до десяти минут отвечал из
// своей копии, не спрашивая сервер, — новый код не доезжал до устройства.
// Теперь HTML и manifest всегда запрашиваются мимо HTTP-кэша (cache:'reload').

const CACHE_VERSION = 5;
const CACHE_NAME = 'options-v' + CACHE_VERSION;

// v5: запрос, для которого свежесть важнее скорости — сама страница.
// Картинки и прочую статику трогать не нужно, они версионируются именем файла.
function isHtmlLike(request) {
  if (request.mode === 'navigate') return true;
  const u = request.url;
  return u.endsWith('.html') || u.endsWith('/for-options/') ||
         u.endsWith('manifest.json') || u.endsWith('sw.js');
}
const STATIC_ASSETS = [
  '/for-options/',
  '/for-options/index.html',
  '/for-options/manifest.json',
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(STATIC_ASSETS).catch(() => {}))
  );
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  if (!event.request.url.startsWith('http')) return;

  // v5: для страницы обходим HTTP-кэш. cache:'reload' заставляет пойти на сервер
  // и заодно обновить сам HTTP-кэш, поэтому старая копия нигде не остаётся.
  // При обрыве связи падаем в кэш, как и раньше, — офлайн не ломается.
  const opts = isHtmlLike(event.request) ? { cache: 'reload' } : undefined;

  event.respondWith(
    fetch(event.request, opts)
      .then(res => {
        if (res.ok && event.request.url.includes('/for-options/')) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
        }
        return res;
      })
      .catch(() => caches.match(event.request).then(hit => hit || Response.error()))
  );
});

// ── IndexedDB helpers ─────────────────────────────
const DB_NAME = 'options-alerts-db';
const DB_VER  = 1;
const STORE   = 'alerts';

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = e => {
      e.target.result.createObjectStore(STORE, { keyPath: 'id' });
    };
    req.onsuccess = e => resolve(e.target.result);
    req.onerror   = e => reject(e.target.error);
  });
}

function dbGetAll(db) {
  return new Promise((resolve, reject) => {
    const tx  = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = e => resolve(e.target.result || []);
    req.onerror   = e => reject(e.target.error);
  });
}

function dbPut(db, obj) {
  return new Promise((resolve, reject) => {
    const tx  = db.transaction(STORE, 'readwrite');
    const req = tx.objectStore(STORE).put(obj);
    req.onsuccess = () => resolve();
    req.onerror   = e => reject(e.target.error);
  });
}

// ── Получение цены с нужной биржи ────────────────
async function fetchPriceForAlert(alert) {
  const asset    = alert.asset;
  const exchange = (alert.exchange || 'bybit').toLowerCase();
  try {
    if (exchange === 'deribit') {
      const r = await fetch(
        `https://www.deribit.com/api/v2/public/get_index_price?index_name=${asset.toLowerCase()}_usd`,
        { signal: AbortSignal.timeout(5000) }
      );
      const d = await r.json();
      return parseFloat(d.result?.index_price || 0);
    } else {
      const r = await fetch(
        `https://api.bybit.com/v5/market/tickers?category=spot&symbol=${asset}USDT`,
        { signal: AbortSignal.timeout(5000) }
      );
      const d = await r.json();
      return parseFloat(d.result?.list?.[0]?.lastPrice || 0);
    }
  } catch(e) {
    console.warn('[SW] price fetch failed for', asset, exchange, e.message);
    return 0;
  }
}

// ── Фоновая проверка алертов ──────────────────────
async function checkAlertsBackground() {
  let db;
  try {
    db = await openDB();
  } catch(e) {
    console.warn('[SW] openDB failed:', e);
    return;
  }

  const allAlerts = await dbGetAll(db);
  const active = allAlerts.filter(a => !a.triggered);
  if (!active.length) return;

  // Дедупликация по "exchange:asset" — параллельные запросы
  const priceMap = new Map();
  const priceKeys = [...new Set(active.map(a => `${(a.exchange||'bybit').toLowerCase()}:${a.asset}`))];
  await Promise.all(priceKeys.map(async key => {
    const [exchange, asset] = key.split(':');
    const price = await fetchPriceForAlert({ asset, exchange });
    priceMap.set(key, price);
  }));

  let firedCount = 0;

  for (const alert of active) {
    const key   = `${(alert.exchange||'bybit').toLowerCase()}:${alert.asset}`;
    const price = priceMap.get(key) || 0;
    if (!price) continue;

    const fired =
      (alert.cond === 'above' && price >= alert.price) ||
      (alert.cond === 'below' && price <= alert.price);
    if (!fired) continue;

    const condText = alert.cond === 'above' ? 'поднялась выше' : 'упала ниже';
    const title = alert.name || `${alert.asset} алерт`;
    const body  = `${alert.asset} ${condText} $${alert.price.toLocaleString()} — сейчас $${Math.round(price).toLocaleString()}`;

    await self.registration.showNotification('🔔 ' + title, {
      body,
      icon:  '/for-options/icon-192.png',
      badge: '/for-options/icon-72.png',
      tag:   'alert-' + alert.id,
      renotify: true,
      data: { url: self.registration.scope, alertId: alert.id }
    });

    alert.triggered   = true;
    alert.triggeredAt = Date.now();
    await dbPut(db, alert);
    firedCount++;
  }

  if (firedCount > 0 && 'setAppBadge' in self.navigator) {
    const allFired = allAlerts.filter(a => a.triggered).length + firedCount;
    await self.navigator.setAppBadge(allFired).catch(() => {});
  }
}

// ── Periodic Background Sync ──────────────────────
self.addEventListener('periodicsync', event => {
  if (event.tag === 'check-price-alerts') {
    event.waitUntil(checkAlertsBackground());
  }
});

// ── Push — от Cloudflare Worker (VAPID) ──────────
self.addEventListener('push', event => {
  let data = {};
  try { data = event.data ? event.data.json() : {}; } catch(e) {}

  const title = data.title || 'Options Alert';
  const body  = data.body  || '';

  event.waitUntil(
    self.registration.showNotification('🔔 ' + title, {
      body,
      icon:     '/for-options/icon-192.png',
      badge:    '/for-options/icon-72.png',
      tag:      'alert-' + (data.alertId || Date.now()),
      renotify: true,
      data:     { url: self.registration.scope, alertId: data.alertId }
    })
  );
});

// ── Клик по уведомлению → открыть/сфокусировать PWA ──
self.addEventListener('notificationclick', event => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || self.registration.scope;
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clientList => {
      for (const client of clientList) {
        if (client.url.startsWith(self.registration.scope) && 'focus' in client) {
          return client.focus();
        }
      }
      return clients.openWindow(targetUrl);
    })
  );
});

// ── Сообщения от страницы ──────────────────────────
self.addEventListener('message', async event => {
  if (!event.data) return;

  if (event.data.type === 'SYNC_ALERTS') {
    try {
      const db = await openDB();
      const tx = db.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      await new Promise((res, rej) => {
        const r = store.clear();
        r.onsuccess = res; r.onerror = rej;
      });
      for (const alert of (event.data.alerts || [])) {
        await new Promise((res, rej) => {
          const r = store.put(alert);
          r.onsuccess = res; r.onerror = rej;
        });
      }
    } catch(e) {
      console.warn('[SW] SYNC_ALERTS error:', e);
    }
  }

  // v5: принудительный сброс кэша со страницы — на случай, когда нужно
  // сбросить всё немедленно, не дожидаясь обновления самого SW.
  if (event.data.type === 'CLEAR_CACHE') {
    try {
      const keys = await caches.keys();
      await Promise.all(keys.map(k => caches.delete(k)));
      event.source?.postMessage({ type: 'CACHE_CLEARED' });
    } catch(e) {
      console.warn('[SW] CLEAR_CACHE error:', e);
    }
  }

  if (event.data.type === 'GET_ALERTS') {
    try {
      const db = await openDB();
      const alerts = await dbGetAll(db);
      event.source?.postMessage({ type: 'ALERTS_DATA', alerts });
    } catch(e) {}
  }
});
