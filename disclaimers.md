# Дисклеймеры — Options Analyst

Готовые тексты для встраивания. Не выдумывай юр. формулировки сверх этих — они согласованы с требованиями Google Play (не брокер, не совет, не бинарные опционы).

---

## 1. In-app дисклеймер — экран первого запуска / "О приложении" (EN)

> **Not financial advice.** Options Analyst is an analytical calculator for educational and informational use only. It is not a broker and does not execute trades. It does not provide personalized investment, trading, tax, or legal advice. All values (P&L, Greeks, prices, alerts) are estimates and may be inaccurate or delayed. Trading options involves a high risk of loss. You are solely responsible for your decisions. See our [Terms of Service] and [Privacy Policy].

## 1. In-app дисклеймер — экран первого запуска / "О приложении" (RU)

> **Не является финансовым советом.** Options Analyst — аналитический калькулятор для образовательных и информационных целей. Он не является брокером и не совершает сделок. Не предоставляет персональных инвестиционных, торговых, налоговых или юридических советов. Все значения (P&L, греки, цены, алерты) — оценки, могут быть неточными или задержанными. Торговля опционами связана с высоким риском убытков. Вы несёте полную ответственность за свои решения. См. [Условия использования] и [Политику конфиденциальности].

---

## 2. Короткий футер-дисклеймер (под расчётами / в подвале экрана)

**EN:** Informational estimates only — not financial advice.
**RU:** Только информационные оценки — не финансовый совет.

---

## 3. Дисклеймер у блока ценовых алертов

**EN:** Alerts are informational notifications based on public market data. Delivery and timing are not guaranteed. Not a trading signal or recommendation.
**RU:** Алерты — информационные уведомления на основе публичных рыночных данных. Доставка и время не гарантируются. Не являются торговым сигналом или рекомендацией.

---

## 4. Дисклеймер при вводе API-ключей

**EN:** Use read-only API keys when possible. Keys are encrypted and stored only on your device — never sent to our servers. You are responsible for key security and exchange compliance.
**RU:** По возможности используйте ключи только для чтения (read-only). Ключи шифруются и хранятся только на вашем устройстве — на наши серверы не передаются. Вы отвечаете за безопасность ключей и соблюдение условий биржи.

---

## 5. Текст для Play Store — раздел "Описание" (в конце)

**EN:**
Options Analyst is an analytical calculator for options traders. Model strategies, estimate P&L and Greeks, view market data from public exchange APIs, and set price alerts.

This app is NOT a broker and does NOT execute trades. It does not provide personalized financial, investment, or trading advice. All calculations are estimates for informational and educational purposes only. Trading derivatives carries a high risk of loss. The app does not offer binary options.

API keys are encrypted and stored only on your device.

**RU:**
Options Analyst — аналитический калькулятор для трейдеров опционами. Моделируйте стратегии, оценивайте P&L и греки, смотрите рыночные данные из публичных API бирж и настраивайте ценовые алерты.

Приложение НЕ является брокером и НЕ совершает сделок. Не предоставляет персональных финансовых, инвестиционных или торговых советов. Все расчёты — оценки для информационных и образовательных целей. Торговля деривативами связана с высоким риском убытков. Приложение не предлагает бинарные опционы.

API-ключи шифруются и хранятся только на вашем устройстве.

---

## 6. Чек-лист Play Console (вне этих файлов — твои действия)

1. **URL Privacy Policy** в Play Console (поле "Privacy Policy") + ссылка внутри приложения. Должна быть публичная, не PDF, не редактируемая → GitHub Pages подходит.
2. **Data safety form** — задекларировать:
   - FCM-токен (Firebase, Google) — сбор для функциональности push.
   - Cloudflare backend — хранение токена/настроек алертов.
   - Финансовая инфо (API-ключи) — хранится только на устройстве, НЕ собирается разработчиком → "data not collected" по этим пунктам, но указать on-device обработку честно.
   - Сторонние SDK (Firebase) считаются — отразить обязательно.
3. **Financial features declaration form** — приложение показывает торговые данные. На вопрос "включает ли финансовые функции" — вероятно ДА (управление/инвестиции деньгами, крипто). Заполни форму честно: калькулятор, не брокер, бинарных опционов нет.
4. **Account/data deletion** — URL удаления данных должен совпадать с указанным в Privacy Policy (у нас — email-запрос; для Play лучше добавить веб-страницу удаления).
5. **In-app ссылки** на Privacy Policy и ToS (требование: privacy policy link или текст ВНУТРИ приложения, не только в Console).
