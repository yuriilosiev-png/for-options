// OPTIONS ANALYZER — Service Worker v3
// Поддержка: кэш ресурсов, Periodic Background Sync, Push, Badge API

const CACHE_NAME = 'options-v3';
const STATIC_ASSETS = [
  '/for-options/',
  '/for-options/index.html',
  '/for-options/manifest.json',
];

// ── Установка SW ──────────────────────────────────
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

// ── Fetch: сначала сеть, fallback кэш ────────────
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  if (!event.request.url.startsWith('http')) return;
  event.respondWith(
    fetch(event.request)
      .then(res => {
        if (res.ok && event.request.url.includes('/for-options/')) {
          const clone = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(event.request, clone));
        }
        return res;
      })
      .catch(() => caches.match(event.request))
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

  // Собираем уникальные активы
  const assets = [...new Set(active.map(a => a.asset))];
  const prices = {};
  for (const asset of assets) {
    try {
      const r = await fetch(
        `https://api.bybit.com/v5/market/tickers?category=spot&symbol=${asset}USDT`,
        { signal: AbortSignal.timeout(5000) }
      );
      const d = await r.json();
      prices[asset] = parseFloat(d.result?.list?.[0]?.lastPrice || 0);
    } catch(e) {
      console.warn('[SW] price fetch failed for', asset, e.message);
    }
  }

  let firedCount = 0;

  for (const alert of active) {
    const price = prices[alert.asset];
    if (!price) continue;

    const fired =
      (alert.cond === 'above' && price >= alert.price) ||
      (alert.cond === 'below' && price <= alert.price);
    if (!fired) continue;

    const condText = alert.cond === 'above' ? 'поднялась выше' : 'упала ниже';
    const title = alert.name || `${alert.asset} алерт`;
    const body  = `${alert.asset} ${condText} $${alert.price.toLocaleString()} — сейчас $${Math.round(price).toLocaleString()}`;

    // Показываем push-уведомление
    await self.registration.showNotification('🔔 ' + title, {
      body,
      icon:  '/for-options/icon-192.png',
      badge: '/for-options/icon-72.png',
      tag:   'alert-' + alert.id,            // группировка одинаковых алертов
      renotify: true,
      data: {
        url: self.registration.scope,
        alertId: alert.id,
      }
    });

    // Помечаем алерт как сработавший в IndexedDB
    alert.triggered = true;
    alert.triggeredAt = Date.now();
    await dbPut(db, alert);
    firedCount++;
  }

  // Обновляем badge на иконке
  if (firedCount > 0 && 'setAppBadge' in self.navigator) {
    // Считаем все сработавшие (для badge показываем общее число)
    const allFired = allAlerts.filter(a => a.triggered).length + firedCount;
    await self.navigator.setAppBadge(allFired).catch(() => {});
  }
}

// ── Periodic Background Sync ──────────────────────
// Chrome Android: запускается браузером ~раз в 15 мин при наличии активных алертов
self.addEventListener('periodicsync', event => {
  if (event.tag === 'check-price-alerts') {
    event.waitUntil(checkAlertsBackground());
  }
});

// ── Push — от Cloudflare Worker (VAPID) ──────────
self.addEventListener('push', event => {
  // PushMessageData.json() синхронный, может бросить исключение
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

// ── Клик по уведомлению → открыть/сфокусировать PWA
self.addEventListener('notificationclick', event => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || self.registration.scope;
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clientList => {
      // Если PWA уже открыта — фокусируем
      for (const client of clientList) {
        if (client.url.startsWith(self.registration.scope) && 'focus' in client) {
          return client.focus();
        }
      }
      // Иначе открываем новое окно
      return clients.openWindow(targetUrl);
    })
  );
});

// ── Сообщения от страницы (для синхронизации алертов) ──
self.addEventListener('message', async event => {
  if (!event.data) return;

  // Страница отправляет алерты в SW для хранения в IndexedDB
  if (event.data.type === 'SYNC_ALERTS') {
    try {
      const db = await openDB();
      const tx = db.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      // Очищаем старые
      await new Promise((res, rej) => {
        const r = store.clear();
        r.onsuccess = res; r.onerror = rej;
      });
      // Записываем актуальные
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

  // Страница запрашивает статус алертов (чтобы обновить после фоновой проверки)
  if (event.data.type === 'GET_ALERTS') {
    try {
      const db = await openDB();
      const alerts = await dbGetAll(db);
      event.source?.postMessage({ type: 'ALERTS_DATA', alerts });
    } catch(e) {}
  }
});
