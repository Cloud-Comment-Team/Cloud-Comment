# Изоляция сессии встраиваемого виджета

## Цель и граница доверия

Код страницы, на которую встроен CloudComment, не считается доверенным. Он может читать и изменять DOM, перехватывать вызовы `fetch`, `postMessage` и Web Storage, подменять конфигурацию загрузчика и прекращать работу iframe. Поэтому host page не получает пароль, bearer-сессию пользователя, frame context, криптографическую подпись или возможность обменять одноразовый ticket.

Доверенными сторонами являются backend CloudComment и код frame bundle, загруженный с контролируемого CloudComment origin. Административная сессия и сессия комментатора имеют разные `audience`, способы передачи и области действия.

Протокол защищает конфиденциальность и целостность учётных данных от JavaScript host page. Он не может гарантировать доступность: владелец страницы всегда может скрыть iframe, не передать ticket или заблокировать сеть. HTTP-заголовок `Origin` можно подделать вне браузера, поэтому он используется вместе с доказательством владения ключом и серверным контекстом, а не как самостоятельная browser attestation.

## Контексты выполнения

### Выделенный widget origin

Предпочтительный режим использует `CLOUD_COMMENT_WIDGET_BASE_URL`, например `https://widget.team13.st.ifbest.org`.

- widget origin публикует только frame shell, widget assets, ограниченный Public Widget API, consent/legal endpoints и health check;
- admin SPA и глобальные admin API на нём возвращают `404`;
- host-only admin-cookie домена административного приложения не отправляется на widget origin;
- iframe использует `sandbox="allow-scripts allow-same-origin allow-popups"`, а его storage относится только к widget origin; загрузки файлов, формы, top-navigation, storage-access и `allow-popups-to-escape-sandbox` не разрешаются;
- `allow-popups` нужен только для прямого открытия юридических документов по пользовательскому клику: frame допускает `_blank` лишь для своего HTTP(S) origin и пути `/legal/`, всегда с `noopener noreferrer`; асинхронного relay через `parent.window.open` нет;
- frame context и WIDGET bearer передаются с `credentials: "omit"` и никогда не зависят от ambient cookie.

Внешний router и сертификат для production widget origin отслеживаются в #196. Репозиторий содержит внутренний Caddy route и CD-smoke, но не имеет доступа к внешнему Traefik.

Выделенный widget origin обязателен. Loader сравнивает origin iframe с origin API до создания iframe; при совпадении показывает безопасную ошибку конфигурации и не выполняет bootstrap. Frame повторно сверяет переданный `apiOrigin` со своим физическим origin и отклоняет совпадение. Opaque sandbox и `Origin: null` не поддерживаются.

## Протокол bootstrap и exchange

1. Loader получает `siteId`, вычисляет канонический `pageUrl` без fragment и создаёт iframe по адресу `/api/public/sites/{siteId}/widget-frame`. В URL нет `pageUrl`, ticket, proof, context или bearer.
2. Backend возвращает динамический HTML shell с `Cache-Control: no-store`, `Referrer-Policy: no-referrer` и CSP `frame-ancestors`, построенным из актуального allowlist сайта. Shell загружает `/widget/cloud-comment-widget-frame.js`, а runtime — только same-origin `/widget/cloud-comment-widget.css`.
3. Frame принимает единственное начальное сообщение от `parent`, проверяет `event.source`, browser-provided `event.origin`, версию протокола, `apiOrigin`, размер полей и одноразовый `instanceId`, затем оставляет только переданный `MessagePort`. В connect дополнительно передаётся только ограниченный `fontFamily` (до 256 символов, без управляющих символов), который применяется через DOM style property; произвольные CSS и HTML не передаются.
4. Frame создаёт неизвлекаемую пару ECDSA P-256 через WebCrypto. Host получает только DER SPKI открытого ключа в base64url.
5. Loader отправляет `POST /api/public/sites/{siteId}/widget-context/bootstrap` с `{ publicKey, pageUrl }`. Браузер сам выставляет origin host page; backend нормализует origin, проверяет allowlist/активность сайта, канонизирует страницу и ограничивает частоту запросов.
6. Backend сохраняет SHA-256 ticket, site, origin, hash страницы, SPKI и канонический URL только в двухминутной bootstrap-записи, необходимой для проверки подписи. Двухчасовой frame context содержит уже только hash страницы. Ответ содержит proof-bound ticket, `expiresAt`, `canonicalPageUrl` и fingerprint открытого ключа.
7. Frame самостоятельно вычисляет SHA-256 fingerprint точного SPKI и отклоняет несовпадение. Приватный ключ подписывает P1363 ECDSA/SHA-256 строку UTF-8:

```text
CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1
{siteId}
{browserObservedParentOrigin}
{canonicalPageUrl}
{publicKeyFingerprint}
{ticket}
```

8. Proof не передаётся parent. Frame напрямую вызывает same-origin `POST /api/public/sites/{siteId}/widget-context/exchange` с `{ ticket, proof }`.
9. Backend сначала выполняет дешёвые проверки размера, срока и binding, затем проверяет подпись и только после успеха атомарно помечает ticket использованным. Неверная подпись не погашает ticket; два конкурентных корректных exchange дают ровно один успех.
10. Raw frame context возвращается только iframe, хранится в памяти или изолированном `sessionStorage` выделенного origin и передаётся в `X-CloudComment-Widget-Context`. Ключ context envelope является SHA-256 scope сайта, parent origin и канонической страницы; независимый ключ commenter bearer ограничен сайтом и parent origin. Raw page URL, context и bearer не входят в имена ключей.

Ticket живёт не более двух минут, frame context — не более двух часов. Raw ticket/context не сохраняются в базе и не записываются в логи.

## Widget bearer

Вход внутри iframe создаёт WIDGET-сессию сроком не более 60 минут. Строка `auth_sessions` связывает её одновременно с:

- `audience = WIDGET_FRAME` в базе (`WIDGET` в доменной модели backend);
- пользователем;
- `site_id`;
- нормализованным embedding origin.

Каждый авторизованный запрос требует одновременно действующий page-scoped frame context и WIDGET bearer. Backend независимо проверяет их scope и принимает пару, только если у обоих совпадают сайт и parent origin. Bearer разрешено переиспользовать между страницами одного сайта на том же embedding origin, чтобы SPA-навигация не требовала повторного входа; другой сайт или origin его не принимает. ADMIN cookie/bearer не принимаются Public Widget API, а WIDGET bearer не принимается глобальными account/admin routes. Миграция V17 отзывает прежние активные WIDGET-сессии и не затрагивает ADMIN.

## Правила API

- Все запросы из frame к site API, включая config и анонимный список, требуют действующий `X-CloudComment-Widget-Context`.
- Auth, мутации комментариев и viewer enrichment требуют одновременно context и связанный WIDGET bearer. Глобальные export/deletion не входят в API виджета и доступны только в админке.
- Запросы комментариев дополнительно передают `X-CloudComment-Page-Url`; backend повторно канонизирует значение и сверяет его с page hash context и обычным параметром/телом API.
- Сайт должен оставаться активным, а embedding origin — присутствовать в актуальном allowlist на каждом запросе. Удаление origin прекращает действие ещё не истёкшего context.
- Старый прямой анонимный config/list без context временно остаётся совместимым, но не может войти, зарегистрироваться, получить viewer enrichment или выполнить мутацию.
- Bootstrap — единственный endpoint с обычным cross-origin CORS host page. Он принимает один непустой HTTP(S) origin, не включает credentials и возвращает `Vary`.
- Exchange и frame API не открывают CORS host origins. `X-CloudComment-Widget-Context` не входит в общий CORS allowlist host page.
- Ошибки invalid/expired/reused/mismatched ticket или context используют один fail-closed envelope без oracle существования сайта, пользователя или сессии.

## Ограничения и очистка

Bootstrap, exchange, widget login и register имеют bounded rate limit по хешированному сочетанию операции, site, origin и remote IP; auth-операции добавляют только hash нормализованного email. Сырые email, IP-ключи и секреты не попадают в диагностические сообщения.

Таблицы ticket/context индексируются по сроку действия. Истёкшие записи не аутентифицируют запросы и удаляются retention-задачей. Для одной комбинации site, origin и fingerprint допускается не более одного активного bootstrap ticket.

Обычный browser reload выделенного frame сначала валидирует сохранённый context запросом config и сохраняет вход комментатора только при успешной проверке. Frame планирует обновление context за 30 секунд до серверного `expiresAt`, учитывает серверный заголовок `Date` и при срабатывании отправляет по закрытому порту строгое событие `context-expired`; loader пересоздаёт frame и выполняет новый bootstrap без раскрытия bearer host-странице. Logout и подтверждённый `401` отзывают или очищают WIDGET bearer вместе с текущим context. `destroy()` очищает только page-scoped context, сохраняет site+origin bearer для навигации SPA и до bounded `destroyed` ack не позволяет loader удалить iframe; поздний ack старого instance не затрагивает новый. Host loader никогда не удаляет admin storage и не получает секрет для такой очистки.

## Обязательная проверка

- ticket разрешённого origin нельзя переслать iframe под другим origin;
- site/context/page swap, удалённый origin и disabled site отклоняются;
- context и bearer разных site/origin, bearer-only и context-only не проходят; новый page context того же site/origin принимает сохранённый bearer;
- invalid, expired, reused и конкурентный ticket дают не более одного успеха;
- malformed/oversized SPKI, другая curve, подпись неверного размера и лишние DER-данные отклоняются до создания context;
- host перехватывает все свои сообщения и storage, но не видит password, proof, context или bearer;
- два page context одного site/origin могут разделить bearer только внутри одной browser session, но не context; другой site/origin и отдельная вкладка не получают этот секрет;
- frame CSP разрешает только текущие origins сайта, а widget origin не отдаёт admin routes;
- URL, history, Caddy/backend logs и GitHub artifacts не содержат ticket, proof, context или bearer;
- выделенная Playwright-конфигурация в Chromium, Firefox и WebKit проверяет dedicated isolation/login/legal, fail-closed при совпадении origins и перенос bearer A→B только в одном site+origin; публикация комментария проверяется полным Chromium-сценарием, а logout, `401`, expiry, reload и опережающее обновление при clock skew — модульными тестами frame/render.
