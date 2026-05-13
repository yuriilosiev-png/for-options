# ⬡ Options Analyst

Опционный аналитик с P&L визуализацией, Greeks, ползунками цены/времени/IV.  
Поддержка Bybit и Deribit. Работает как PWA (устанавливается на Android/iOS).

## 📁 Структура файлов

```
/
├── index.html      ← основное приложение (переименуй options-analyzer.html)
├── manifest.json   ← PWA манифест
├── sw.js           ← Service Worker (оффлайн-кэш)
├── icon-192.png    ← иконка 192×192 (добавь вручную)
├── icon-512.png    ← иконка 512×512 (добавь вручную)
└── README.md
```

## 🚀 Деплой на GitHub Pages

1. Создай репозиторий на GitHub (например `options-analyst`)
2. Загрузи все файлы в корень репозитория
3. Переименуй `options-analyzer.html` → `index.html`
4. GitHub → Settings → Pages → Branch: `main` / `root`
5. Через 1-2 минуты приложение доступно по адресу:  
   `https://YOUR_USERNAME.github.io/options-analyst/`

## 📱 Установка как APK (Android)

### Способ 1 — PWA Builder (рекомендуется)
1. Открой [pwabuilder.com](https://pwabuilder.com)
2. Вставь URL своего GitHub Pages
3. Нажми **Package for stores** → **Android**
4. Скачай APK и установи на телефон

### Способ 2 — Браузер Chrome на Android
1. Открой URL приложения в Chrome
2. Меню (⋮) → **Добавить на главный экран**
3. Приложение установится как PWA (работает без браузера)

### Способ 3 — iOS Safari
1. Открой URL в Safari
2. Поделиться (↑) → **На экран «Домой»**

## 🔌 Подключение к бирже

### Bybit
1. Bybit → Аккаунт → API Management → Создать ключ
2. Права: **Read** (для чтения позиций)
3. Вставь Key + Secret в приложении → кнопка **API**

### Deribit
1. Deribit → Account → API → Add key
2. Scope: `read` (или `trade` для ордеров)
3. Можно протестировать на testnet.deribit.com

## ⚙️ Функционал

- 📊 P&L график с двумя кривыми (сегодня + экспирация)
- 🎚 Вертикальный ползунок **цены** (слева)
- 📅 Горизонтальный ползунок **времени** (снизу)
- 🌊 Ползунок **IV** сдвига
- 🇬🇷 **Greeks**: Δ Delta, Γ Gamma, ν Vega, Θ Theta, ρ Rho
- ⚖️ **R:R** соотношение, Max Profit/Loss, Breakeven точки
- 🔄 Δ-хеджирование (расчёт нейтральной позиции)
- 📐 10 стратегий: Стрэддл, Стрэнгл, Бабочка, Кондор, Флягонал, Колесо...
- 🔄 Поворот экрана (кнопка ⟳)
- 📡 Авто-обновление цены каждые 15 сек (публичный API)
- 📴 Оффлайн-режим через Service Worker
