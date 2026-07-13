# MVP API contracts and smoke checklist

Этот документ фиксирует минимальный контракт для MVP, чтобы backend, admin и widget не расходились по route names, response shapes и auth-flow.

OpenAPI / springdoc — отдельная задача после MVP. До активной frontend/widget интеграции этот файл является source of truth.

## Статус реализации

| Область | Статус | Примечание |
| --- | --- | --- |
| Health | реализовано | `GET /api/health` |
| Auth | реализовано | register, login, logout, me |
| Unified error envelope | реализовано | `ApiErrorHandler`, `ApiErrorCode` |
| Admin sites / embed code | реализовано | `/api/sites/**`, embed-code |
| Owner analytics | реализовано | `/api/analytics/owner`, dashboard + site detail |
| Moderation | реализовано | `/api/moderation/**`, owner-scoped queue/actions |
| Account deletion | реализовано | `/api/account/**`, email confirmation flow |
| Public widget API | реализовано | `/api/public/sites/{siteId}/**`, comments/config |
| Widget bundle / bootstrap | реализовано | public API comments + auth form |
| Realtime-обновления | реализовано | `/api/realtime/tickets`, `/api/realtime/ws`, новые комментарии и действия модерации |
| Постоянные уведомления | реализовано | `/api/notifications`, счётчик непрочитанных, отметка одного или всех уведомлений |
| Диагностика установки | реализовано | `/api/sites/{siteId}/installation-status`, последние безопасные origin-события и контрольный список |

## Auth flow

1. Перед регистрацией, входом, выходом и любым изменяющим admin-запросом клиент вызывает `GET /api/auth/csrf`. Backend устанавливает HttpOnly CSRF cookie и возвращает имя заголовка со случайным значением для памяти вкладки.
2. Клиент отправляет CSRF-значение в указанном заголовке. Отсутствующая или несовпадающая пара cookie/header возвращает `403 INVALID_CSRF_TOKEN`.
3. `POST /api/auth/login` создаёт сессию с audience `ADMIN`, возвращает только `expiresAt` и `user`, а raw session token устанавливает в HttpOnly cookie. В production используется host-only cookie `__Host-cloud_comment_admin_session` с `Secure`, `SameSite=Strict`, `Path=/` и без `Domain`.
4. Защищённые admin endpoints принимают только эту cookie и только сессию audience `ADMIN`; `Authorization: Bearer`, widget bearer и legacy-сессии для них отклоняются. `GET /api/auth/me` возвращает текущего пользователя по cookie-сессии.
5. `POST /api/auth/logout` идемпотентно отзывает найденную сессию, всегда очищает cookie и возвращает `204`.
6. Невалидная, просроченная или отозванная cookie-сессия → `401` с `error.code = INVALID_SESSION`.
7. Неверные credentials → `401` с `error.code = INVALID_CREDENTIALS`.
8. `/api/public/sites/**` не использует admin cookie: открытые операции работают без неё, а действия пользователя виджета принимают только bearer-сессию audience `WIDGET`. Site/origin scope и iframe-изоляция этой сессии добавляются в #168.

Миграция audience однократно отзывает все ранее выданные неразмеченные сессии. Поэтому после выпуска #167 admin и widget пользователи входят заново; неизвестная legacy-сессия не принимается ни одним новым каналом. Значение `LEGACY` сохраняется только как rollback-совместимый default для предыдущей версии backend и всегда отклоняется новым кодом.

Admin frontend использует same-origin `apiClient` с `baseURL = /api`, `withCredentials = true` и путями `/auth/*`. CSRF-токен хранится только в памяти, запросы его получения дедуплицируются, а `403 INVALID_CSRF_TOKEN` допускает один безопасный refresh/retry. Legacy-ключ `cloud-comment.admin.authToken` при старте только удаляется; его значение не читается и не мигрируется.

## Realtime-уведомления

Админка сначала вызывает защищённый cookie-сессией и CSRF `POST /api/realtime/tickets`, затем открывает `WebSocket /api/realtime/ws?ticket=<one-time-ticket>`. Ticket привязан к владельцу, живёт не более 60 секунд и атомарно поглощается при handshake; session token в URI не попадает.

Админка держит одно WebSocket-соединение на авторизованную сессию и раздает события внутренним подписчикам: notification center, очередь модерации, общий dashboard и аналитика конкретного сайта.

Центр уведомлений загружает постоянное состояние через защищённые owner-scoped маршруты:

- `GET /api/notifications?page=1&pageSize=20` — страница уведомлений в порядке от новых к старым;
- `GET /api/notifications/unread-count` — `{ "unreadCount": 3 }`;
- `PATCH /api/notifications/{notificationId}/read` — отметить одно уведомление и вернуть его актуальное представление;
- `POST /api/notifications/read-all` — отметить все уведомления владельца, ответ `204`.

Элемент списка содержит `id`, `commentId`, `siteId`, `siteName`, `pageId`, `pageUrl`, `parentId`, `authorEmail`, `contentPreview`, `status`, `readAt` и `createdAt`. Сведения о комментарии и странице вычисляются из текущих связанных данных. Уведомления старше 90 дней удаляются; чужой или отсутствующий `notificationId` возвращает одинаковый `404`.

Подтверждение подключения:

```json
{
  "type": "realtime.connected",
  "payload": {
    "status": "connected"
  }
}
```

Уведомление о новом комментарии:

```json
{
  "type": "comment.created",
  "sentAt": "2026-07-05T08:00:00Z",
  "payload": {
    "notificationId": "uuid",
    "commentId": "uuid",
    "siteId": "uuid",
    "siteName": "Demo site",
    "pageId": "uuid",
    "pageUrl": "https://example.com/page",
    "parentId": null,
    "authorEmail": "visitor@example.com",
    "contentPreview": "Comment preview",
    "status": "PENDING",
    "createdAt": "2026-07-05T08:00:00Z"
  }
}
```

Уведомление о действии модерации:

```json
{
  "type": "comment.moderation_action_applied",
  "sentAt": "2026-07-05T08:01:00Z",
  "payload": {
    "siteId": "uuid",
    "pageId": "uuid",
    "commentId": "uuid",
    "action": "APPROVE",
    "fromStatus": "PENDING",
    "toStatus": "APPROVED",
    "reason": "Looks good",
    "moderatorId": "uuid",
    "createdAt": "2026-07-05T08:01:00Z"
  }
}
```

События owner-scoped: backend отправляет `comment.created` только активным сессиям владельца из `sites.owner_id`, а `comment.moderation_action_applied` — активным сессиям владельца, который выполнил действие. `notificationId` совпадает с идентификатором постоянной записи, поэтому повторная доставка не создаёт дубликат в интерфейсе. Очередь модерации и аналитика обновляются по этим событиям без ручного reload.

## Диагностика установки

`GET /api/sites/{siteId}/installation-status` доступен только владельцу сайта и возвращает:

- `status`: `NEVER_SEEN`, `HEALTHY`, `STALE` или `REJECTED`;
- `reason`: `WIDGET_NOT_SEEN`, `RECENT_SUCCESS`, `SUCCESS_STALE`, `ORIGIN_CONFIGURATION_CHANGED`, `ORIGIN_REJECTED` или `SITE_INACTIVE`;
- контрольные признаки `siteCreated`, `originConfigured`, `widgetSeen`, `firstCommentReceived`;
- последние нормализованные `lastSuccessfulOrigin`/`lastSuccessfulAt` и `lastRejectedOrigin`/`lastRejectedAt`.

`HEALTHY` требует успешного ответа `GET /api/public/sites/{siteId}/config` с origin, который всё ещё разрешён, не старше 24 часов. CORS preflight сам по себе успех не фиксирует. Свежий подтверждённый успех имеет приоритет над более новым неподтверждённым отклонением, поэтому поддельный публичный запрос не может перевести работающую установку в `REJECTED`; админка всё равно показывает более новую отклонённую попытку отдельным предупреждением. Для установки без свежего успеха более новое отклонение `/config` даёт `REJECTED`; неактивный сайт также получает `REJECTED`, старый успех — `STALE`, а удаление успешного origin из текущей конфигурации — `STALE` с `ORIGIN_CONFIGURATION_CHANGED`. Чужой UUID маскируется как `404`. API не принимает URL страницы и не выполняет внешних запросов. Отклонённое событие создают только фактический `GET /config` и CORS preflight, запрашивающий метод `GET`; другие методы диагностическое состояние не меняют.

## Unified error envelope

Все ошибки API используют один формат:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "status": 400,
    "path": "/api/auth/register",
    "fields": [
      {
        "field": "email",
        "message": "must be a well-formed email address",
        "code": "Email"
      }
    ]
  }
}
```

Правила:

- `code` — строковое имя из `ApiErrorCode`.
- `path` — запрошенный URI.
- `fields` — массив; для не-validation ошибок может быть пустым.
- Отсутствующая или невалидная admin cookie на защищённом endpoint → `401 INVALID_SESSION`.
- Отсутствующая или несовпадающая CSRF-пара cookie/header на изменяющем admin endpoint → `403 INVALID_CSRF_TOKEN`.
- Чужой или несуществующий owned resource → `404 NOT_FOUND` с `message: "Resource not found"`.

Коды `ApiErrorCode`: `BAD_REQUEST`, `BUSINESS_ERROR`, `EMAIL_ALREADY_USED`, `INVALID_CREDENTIALS`, `INVALID_CSRF_TOKEN`, `INVALID_SESSION`, `INTERNAL_ERROR`, `MALFORMED_JSON`, `METHOD_NOT_ALLOWED`, `NOT_FOUND`, `TYPE_MISMATCH`, `UNSUPPORTED_MEDIA_TYPE`, `VALIDATION_FAILED`.

## Route map

Все backend routes начинаются с `/api/`. Caddy проксирует `/api/*` на backend.

### Health

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/health` | `GET` | public | Smoke check for backend availability |

### Auth

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/auth/csrf` | `GET` | public | Issue HttpOnly CSRF cookie and return the matching in-memory header value |
| `/api/auth/register` | `POST` | CSRF | Create a user account |
| `/api/auth/login` | `POST` | CSRF | Exchange credentials for an HttpOnly admin session cookie |
| `/api/auth/logout` | `POST` | CSRF, cookie optional | Idempotently revoke the current session and clear the cookie |
| `/api/auth/me` | `GET` | HttpOnly admin cookie | Return current authenticated user |

### Privacy

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/privacy/consent-requirements` | `GET` | public | Current consent document versions and URLs |

Widget register uses the same request body as admin register (see below).

#### `GET /api/privacy/consent-requirements`

The endpoint is intentionally readable cross-origin with `Access-Control-Allow-Origin: *`: embedded widgets need the same public document versions before registration, no credentials are accepted by this request, and no private account data is returned. `OPTIONS` preflight for `GET` is public as well.

Relative legal-document URLs are resolved by the widget against its configured CloudComment `apiBaseUrl`, never against the embedding page origin. Absolute URLs remain unchanged, allowing deployments to configure an external CloudComment-controlled legal host when needed.

Response `200`:

```json
{
  "privacyPolicyVersion": "2026-07-12",
  "termsVersion": "2026-07-01",
  "privacyPolicyUrl": "/legal/privacy-policy.html",
  "termsUrl": "/legal/terms.html",
  "personalDataNoticeUrl": "/legal/personal-data-notice.html",
  "dataExportInfoUrl": "/legal/personal-data-notice.html#data-export"
}
```

#### `POST /api/auth/register`

Request:

```json
{
  "email": "owner@example.com",
  "password": "strong-password",
  "acceptedPrivacyPolicy": true,
  "acceptedTerms": true,
  "privacyPolicyVersion": "2026-07-12",
  "termsVersion": "2026-07-01"
}
```

Missing required consent or outdated document version → `400 VALIDATION_FAILED`.

Response `201`:

```json
{
  "id": "uuid",
  "email": "owner@example.com",
  "roles": ["COMMENTER"],
  "createdAt": "2026-06-28T18:00:00Z",
  "updatedAt": "2026-06-28T18:00:00Z"
}
```

#### `POST /api/auth/login`

Request — тот же shape, что register.

Response `200`:

```json
{
  "expiresAt": "2026-06-29T18:00:00Z",
  "user": {
    "id": "uuid",
    "email": "owner@example.com",
    "roles": ["OWNER"],
    "createdAt": "2026-06-28T18:00:00Z",
    "updatedAt": "2026-06-28T18:00:00Z"
  }
}
```

#### `GET /api/auth/me`

Response `200` — тот же shape, что `user` в login response.

#### `POST /api/auth/logout`

Response `204`, body отсутствует.

### Admin backend API

Все admin endpoints требуют HttpOnly admin session cookie с audience `ADMIN` и проверку владения через `sites.owner_id` (см. `docs/data-model.md`). Изменяющие запросы дополнительно требуют совпадающую CSRF-пару cookie/header. Bearer-заголовок, widget token или legacy token не являются совместимым способом входа в admin API.

| Route | Method | Purpose |
| --- | --- | --- |
| `/api/sites` | `GET` | List sites owned by current user |
| `/api/sites` | `POST` | Create a site |
| `/api/sites/{siteId}` | `GET` | Get site details |
| `/api/sites/{siteId}` | `PATCH` | Update site name, domain, moderation mode, active flag |
| `/api/sites/{siteId}` | `DELETE` | Delete owned site and its related widget data |
| `/api/sites/{siteId}/allowed-origins` | `PUT` | Replace allowed widget origins |
| `/api/sites/{siteId}/embed-code` | `GET` | Get embed snippet for the site |
| `/api/sites/{siteId}/automoderation/policies` | `GET` | Получить активную политику, черновик и историю версий |
| `/api/sites/{siteId}/automoderation/policies` | `POST` | Создать единственный черновик политики |
| `/api/sites/{siteId}/automoderation/policies/{policyId}` | `PATCH` | Сохранить черновик с optimistic revision |
| `/api/sites/{siteId}/automoderation/policies/{policyId}?expectedRevision={revision}` | `DELETE` | Удалить согласованную revision неопубликованного черновика |
| `/api/sites/{siteId}/automoderation/policies/{policyId}/simulate` | `POST` | Смоделировать решение без записи комментария |
| `/api/sites/{siteId}/automoderation/policies/{policyId}/publish` | `POST` | Опубликовать согласованную revision черновика |
| `/api/sites/{siteId}/automoderation/versions/{versionId}/rollback` | `POST` | Создать новую опубликованную версию из старой |
| `/api/analytics/owner` | `GET` | Owner-scoped analytics for dashboard and site detail |
| `/api/discussions` | `GET` | Получить опубликованные корневые ветки владельца |
| `/api/discussions/{rootCommentId}` | `GET` | Получить опубликованные сообщения выбранной ветки |
| `/api/moderation/comments` | `GET` | Получить страницу комментариев в очереди модерации |
| `/api/moderation/counts` | `GET` | Получить owner-scoped счётчики по статусам |
| `/api/moderation/comments/{commentId}` | `GET` | Получить подробности комментария |
| `/api/moderation/comments/{commentId}/flags` | `PATCH` | Изменить owner-scoped флаги закрепления и избранного |
| `/api/moderation/comments/{commentId}/actions` | `POST` | Применить одиночное действие модерации |
| `/api/moderation/comments/bulk-actions` | `POST` | Применить действие к 1–50 комментариям с частичным результатом |
| `/api/moderation/actions/{actionId}/undo` | `POST` | Отменить последнее допустимое действие модерации |
| `/api/moderation/operations/{operationId}/undo` | `POST` | Отменить допустимые элементы массовой операции с частичным результатом |
| `/api/moderation/comments/{commentId}/automoderation-feedback` | `PUT` | Идемпотентно записать false positive/negative |
| `/api/moderation/comments/{commentId}/automoderation-feedback` | `DELETE` | Удалить собственную отметку качества |

`GET /api/moderation/comments` поддерживает необязательные фильтры `siteId`, `pageId`, `pageUrl`, `status`, повторяемый `statuses`, `createdFrom`, `createdTo`, `search`, `favorite`, `page`, `pageSize`, `sortBy`, `sortOrder`. Одновременная передача `status` и `statuses` возвращает `400 BAD_REQUEST`. `sortBy` дополнительно принимает `PINNED` и `FAVORITE`; сортировка по умолчанию — `sortBy=SMART&sortOrder=DESC`.

`GET /api/discussions` возвращает только опубликованные корневые ветки сайтов текущего владельца. Необязательные query-параметры: `siteId`, `view=ALL|UNREAD|NEEDS_REPLY`, `search`, `page`, `pageSize`. Поиск охватывает сайт, заголовок/URL страницы и любую опубликованную реплику ветки. Сначала идут непрочитанные и ожидающие ответа ветки, затем остальные по последней активности. `GET /api/discussions/{rootCommentId}` маскирует чужой, скрытый, удалённый и отсутствующий корень одинаковым `404 NOT_FOUND`. Оба ответа содержат только безопасное `displayName` и признак `owner`; email автора не включается.

#### `GET /api/analytics/owner`

Query:

- `range`: `7d`, `30d`, `90d` или `all`; по умолчанию `30d`.
- `siteId`: необязательный UUID. При наличии аналитика ограничивается одним сайтом владельца; чужой или отсутствующий сайт возвращает единый `404 NOT_FOUND` с `Resource not found`.
- `timeZone`: IANA-идентификатор часового пояса, например `Europe/Moscow`; по умолчанию `UTC`. Неизвестное значение возвращает `400 BAD_REQUEST`.

Response `200`:

```json
{
  "range": "30d",
  "siteId": null,
  "timeZone": "Europe/Moscow",
  "bucketGranularity": "DAY",
  "from": "2026-06-05T21:00:00Z",
  "to": "2026-07-05T15:30:00Z",
  "summary": {
    "sites": 2,
    "pages": 8,
    "comments": 42,
    "replies": 9,
    "reactions": 18,
    "pending": 4,
    "approved": 31,
    "rejected": 2,
    "hidden": 1,
    "spam": 4
  },
  "commentsOverTime": [
    {
      "bucket": "2026-07-01",
      "total": 5,
      "approved": 4,
      "pending": 1,
      "spam": 0
    }
  ],
  "comparison": {
    "previousFrom": "2026-05-06T21:00:00Z",
    "previousTo": "2026-06-05T15:30:00Z",
    "comments": {
      "current": 42,
      "previous": 35,
      "absoluteChange": 7,
      "percentageChange": 20.0
    },
    "reactions": { "current": 18, "previous": 20, "absoluteChange": -2, "percentageChange": -10.0 },
    "automaticDecisions": { "current": 31, "previous": 0, "absoluteChange": 31, "percentageChange": null },
    "manualDecisions": { "current": 7, "previous": 9, "absoluteChange": -2, "percentageChange": -22.2 },
    "undoActions": { "current": 1, "previous": 0, "absoluteChange": 1, "percentageChange": null }
  },
  "workload": {
    "requiringDecision": 8,
    "oldestPendingAt": "2026-07-01T08:00:00Z",
    "automaticDecisions": 31,
    "manualDecisions": 7,
    "undoActions": 1
  },
  "moderationDistribution": [
    { "status": "APPROVED", "count": 31 },
    { "status": "PENDING", "count": 4 },
    { "status": "SPAM", "count": 4 },
    { "status": "REJECTED", "count": 2 },
    { "status": "HIDDEN", "count": 1 }
  ],
  "moderationFunnel": [
    { "status": "APPROVED", "count": 31 },
    { "status": "PENDING", "count": 4 },
    { "status": "SPAM", "count": 4 },
    { "status": "REJECTED", "count": 2 },
    { "status": "HIDDEN", "count": 1 }
  ],
  "reactionDistribution": [
    { "type": "LIKE", "count": 12 }
  ],
  "topPages": [
    {
      "pageId": "uuid",
      "siteId": "uuid",
      "siteName": "Example site",
      "pageUrl": "https://example.com/post",
      "comments": 12,
      "replies": 3,
      "reactions": 7,
      "approved": 10,
      "pending": 1,
      "spam": 1,
      "lastCommentAt": "2026-07-04T12:00:00Z"
    }
  ],
  "activeCommenters": [
    {
      "userId": "uuid",
      "email": "author@example.com",
      "displayName": "Author",
      "comments": 6,
      "approved": 5,
      "pending": 1,
      "rejectedOrSpam": 0,
      "lastActivityAt": "2026-07-04T12:00:00Z"
    }
  ]
}
```

Rules:

- Все временные интервалы полуоткрытые: `[from, to)`. Начало вычисляется как локальная полночь первого дня в `timeZone`; границы предыдущего периода сохраняют то же локальное время суток и корректны на переходах DST.
- `7d` и `30d` возвращают дневные интервалы, `90d` — ISO-недели с понедельника, `all` — месяцы. Пропущенные интервалы заполняются нулями; `bucketGranularity` явно содержит `DAY`, `WEEK` или `MONTH`.
- `comparison` равно `null` только для `all`. Если предыдущее значение равно нулю, `percentageChange` также равно `null`, а не бесконечности. Для остальных диапазонов сравниваются комментарии, реакции, автоматические и ручные решения, а также отмены.
- `workload.requiringDecision` и `oldestPendingAt` описывают текущую очередь `PENDING + SPAM` без ограничения выбранным периодом. Остальные workload-счётчики относятся к выбранному периоду. `automaticDecisions` учитывает только применённые оценки `LIVE`, но не наблюдения `SHADOW`.
- История автоматических решений заполняется PostgreSQL-trigger при полном изменившемся automod-snapshot. Поэтому совместимый предыдущий backend после rollback продолжает создавать события, а новый backend не пишет вторую дублирующую строку.
- Комментарии, ответы, статусы и популярные страницы используют `comments.created_at` и исключают мягко удалённые комментарии (`deleted_at is null`). Реакции используют `comment_reactions.created_at` с тем же правилом видимости.
- `moderationDistribution` — каноническое название распределения статусов. Устаревший `moderationFunnel` в течение одного совместимого релиза возвращает тот же список.

### Account privacy API

Все account endpoints, кроме confirmation, требуют HttpOnly admin session cookie; изменяющие запросы дополнительно требуют CSRF.

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/account/deletion-requests` | `POST` | admin cookie + CSRF | Create or refresh active account deletion request and send confirmation email |
| `/api/account/deletion-requests/current` | `GET` | admin cookie | Return active deletion request status, if any |
| `/api/account/personal-data` | `GET` | admin cookie | Export current user's personal data snapshot |
| `/api/account/deletion-confirmations` | `POST` | public | Confirm account deletion with one-time token from email |

Frontend confirmation route:

- `/account/deletion-confirm?token=...` automatically confirms the token from email.
- `/account/deletion-confirm` shows a manual token input for users who prefer not to open email links or whose mail client strips query parameters.

#### `POST /api/account/deletion-requests`

Response `201`:

```json
{
  "id": "uuid",
  "userId": "uuid",
  "status": "PENDING",
  "createdAt": "2026-06-28T18:00:00Z",
  "expiresAt": "2026-06-29T18:00:00Z"
}
```

Повторный запрос переиспользует активную заявку пользователя, ротирует confirmation token и отправляет email снова.

#### `POST /api/account/deletion-confirmations`

Request:

```json
{
  "token": "one-time-confirmation-token"
}
```

Response `204`.

После подтверждения:
- аккаунт помечается удалённым и деактивируется;
- email и display name анонимизируются;
- все активные `auth_sessions` пользователя отзываются;
- owned sites удаляются каскадно;
- комментарии пользователя на чужих сайтах обезличиваются и заменяют body на deleted marker;
- moderation actions удаленного пользователя больше не содержат `moderator_id`;
- реакции удаленного пользователя удаляются из `comment_reactions`;
- повторное использование token → `409 BUSINESS_ERROR`;
- просроченный token → `409 BUSINESS_ERROR`;
- неизвестный token → `404 NOT_FOUND`.

Mail delivery:
- local/test: `cloud-comment.mail.mode=log` (письмо не уходит наружу);
- production: `cloud-comment.mail.mode=smtp` + SMTP env vars.

#### `GET /api/account/personal-data`

Response `200`:

```json
{
  "account": {
    "id": "uuid",
    "email": "owner@example.com",
    "displayName": null,
    "enabled": true,
    "createdAt": "2026-06-28T18:00:00Z",
    "updatedAt": "2026-06-28T18:00:00Z",
    "deletedAt": null
  },
  "roles": ["COMMENTER"],
  "consents": [
    {
      "privacyPolicyVersion": "2026-07-01",
      "termsVersion": "2026-07-01",
      "source": "ADMIN",
      "acceptedAt": "2026-06-28T18:00:00Z"
    }
  ],
  "sessions": {
    "active": 1,
    "revoked": 0,
    "expired": 0
  },
  "resources": {
    "ownedSites": 1,
    "ownedPages": 1,
    "ownedComments": 2,
    "authoredComments": 3,
    "moderationActions": 4,
    "commentReactions": 5
  },
  "deletionRequest": null,
  "exportedAt": "2026-06-28T18:00:00Z"
}
```

Export пишет privacy audit event `PERSONAL_DATA_EXPORTED`. Response не содержит password, bearer/session token или confirmation token.

Privacy audit / retention:
- `privacy_events` хранит consent accepted, deletion requested/confirmed/deleted, personal data exported и retention cleanup events;
- audit metadata не содержит raw password, bearer/session token или confirmation token;
- retention cleanup закрывает expired pending deletion requests и удаляет старые inactive sessions/deletion requests по `cloud-comment.privacy.*` настройкам.

#### `POST /api/sites`

Request:

```json
{
  "name": "Example site",
  "domain": "example.com",
  "moderationMode": "PRE_MODERATION",
  "allowedOrigins": ["https://example.com"],
  "widgetStyle": {
    "theme": "AUTO",
    "accentColor": "#0f766e",
    "cornerRadius": "MEDIUM"
  }
}
```

`moderationMode`: `PRE_MODERATION` | `POST_MODERATION` | `DISABLED`.

`allowedOrigins`: 1..20 origins.

### Admin SPA routes

Frontend route map зафиксирован в `frontend/admin/src/routes/index.tsx`:

| Route | Purpose |
| --- | --- |
| `/login` | Login page |
| `/` | Dashboard |
| `/comments` | Comments page |
| `/users` | Users page |
| `/moderation` | Moderation page |
| `/statistics` | Statistics page |
| `/settings` | Settings page |

Неавторизованный доступ к protected routes перенаправляется на `/login`.

### Public widget API

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/public/sites/{siteId}/config` | `GET` | public + origin check | Public widget configuration |
| `/api/public/sites/{siteId}/auth/register` | `POST` | public + origin check | Create a visitor account from the widget |
| `/api/public/sites/{siteId}/auth/login` | `POST` | public + origin check | Exchange widget visitor credentials for bearer token |
| `/api/public/sites/{siteId}/auth/me` | `GET` | bearer + origin check | Return current widget visitor profile |
| `/api/public/sites/{siteId}/auth/logout` | `POST` | self-auth bearer + origin check | Revoke current widget visitor session |
| `/api/public/sites/{siteId}/account/personal-data` | `GET` | bearer + origin check | Export current widget visitor personal data |
| `/api/public/sites/{siteId}/account/deletion-requests` | `POST` | bearer + origin check | Request account deletion email confirmation from the widget |
| `/api/public/sites/{siteId}/pages/comments` | `GET` | public + origin check | List comments for a page |
| `/api/public/sites/{siteId}/pages/comments` | `POST` | bearer + origin check | Create a comment for authenticated visitor |
| `/api/public/sites/{siteId}/comments/{commentId}` | `PATCH` | bearer + origin check | Edit current visitor's own comment |
| `/api/public/sites/{siteId}/comments/{commentId}` | `DELETE` | bearer + origin check | Soft-delete current visitor's own comment |
| `/api/public/sites/{siteId}/comments/{commentId}/reaction` | `PUT` | bearer + origin check | Set or clear current visitor reaction |

Query для list:

- `pageUrl` — URL страницы обсуждения (обязателен)
- `page`, `pageSize` — pagination (опционально)

Request для create:

```json
{
  "pageUrl": "https://example.com/blog/post-1",
  "parentId": null,
  "content": "Comment text"
}
```

`parentId` is `null` for a root comment or an approved root comment id for a one-level reply. Invalid, foreign, or non-approved parent comments are masked as `404 NOT_FOUND`.

Request для edit:

```json
{
  "content": "Updated comment text"
}
```

Edit/delete доступны только автору комментария с текущей bearer-сессией и проходят ту же domain policy по `Origin`. Чужой, удаленный, отсутствующий или не относящийся к сайту комментарий маскируется как `404 NOT_FOUND`.

Request для реакции:

```json
{
  "type": "LOVE"
}
```

`type`: `LIKE` | `LOVE` | `LAUGH` | `WOW` или `null`, чтобы убрать реакцию текущего пользователя.

Правила public widget API:

- `siteId` берется из path и соответствует `data-site-id` в embed-code.
- `Origin` обязателен и должен exact-match одну из записей `site_allowed_origins` активного сайта.
- `pageUrl` должен быть absolute `http/https` URL того же origin, что и request `Origin`. До поиска или создания страницы backend удаляет fragment и приватные query-параметры по таблице ниже; поэтому та же политика действует для старых клиентов. Чужой origin проверяется уже у канонического URL и по-прежнему маскируется как `404`.
- Bad/missing/disallowed origin, inactive/missing site и parent comment не с этой страницы возвращают `404 NOT_FOUND` с `Resource not found`.
- Публичный список возвращает только `APPROVED`; `PENDING`, `REJECTED`, `HIDDEN`, `SPAM` не видны в виджете.
- Approved replies are returned inside the root comment `replies` array. The widget exposes reply creation only from root comments and renders replies one level deep.
- Public list accepts `sort=PINNED_FIRST|NEWEST|OLDEST|TOP_REACTIONS`. Approved pinned roots always come first; reaction sorting counts reactions on the root and approved replies. Public responses expose `pinned`, but never the internal `favorite` flag.
- Авторизованный visitor видит в public list флаг `ownedByCurrentUser` для своих комментариев; виджет использует его, чтобы показать edit/delete controls только владельцу.
- Редактирование заново проходит автомодерацию сайта: чистый текст может остаться/стать `APPROVED`, сомнительный попасть в `PENDING`, а явно запрещенный — в `SPAM`.
- Удаление комментария автором soft-delete'ит запись: комментарий исчезает из public list и moderation queue, но база сохраняет факт удаления для целостности истории.
- `reactions` возвращаются для корневых комментариев и ответов; при наличии bearer token публичный list помечает реакцию текущего пользователя через `reactedByCurrentUser`.
- Embedded widget auth uses site-scoped `/api/public/sites/{siteId}/auth/*` aliases, not `/api/auth/*`, so browser CORS preflight is checked against the same domain policy.
- CORS preflight разрешает только exact-match origin, методы `GET`, `POST`, `PUT`, `PATCH`, `DELETE` и заголовки `Authorization`, `Content-Type`, `Accept`. Неизвестный метод, лишний заголовок или чужой origin получают `404` без `Access-Control-Allow-Origin`; credentials и wildcard не разрешаются.
- Виджет загружает корневые комментарии страницами по 20 и показывает «Показать ещё комментарии», пока доступны следующие страницы. Ответы объединяются без дублей по `id`; смена сортировки сбрасывает список на первую страницу и не применяет устаревший ответ предыдущего запроса.
- Валидная widget bearer-сессия добавляет в публичные root/reply списки только собственные комментарии автора со статусом `PENDING`; `anonymous`, истёкшая сессия и другой пользователь видят только `APPROVED`. `totalItems`, `replyCount` и пагинация вычисляются по той же viewer-aware выборке и не раскрывают чужую очередь.
- Самообслуживание аккаунта в виджете использует site-scoped aliases `/api/public/sites/{siteId}/account/*`, а не `/api/account/*`: экспорт персональных данных и запрос удаления работают с external origin без ослабления глобального CORS.
- Создание комментария требует bearer token; guest-flow в MVP не поддерживается.

Политика канонизации query у widget и backend одинакова:

| Класс | Percent-decoded имя параметра, без учёта регистра | Результат |
| --- | --- | --- |
| Tracking prefix | `utm_*` | Удаляется |
| Tracking exact | `fbclid`, `gclid`, `dclid`, `msclkid`, `yclid`, `twclid`, `ttclid`, `igshid`, `li_fat_id`, `_ga`, `_gl`, `mc_cid`, `mc_eid`, `gbraid`, `wbraid`, `srsltid`, `gad_source`, `gad_campaignid` | Удаляется |
| Sensitive exact | `access_token`, `id_token`, `refresh_token`, `auth_token`, `authorization`, `api_key`, `apikey`, `jwt`, `password`, `session`, `session_id`, `sessionid`, `token`, `client_secret`, `secret`, `signature`, `sig`, `otp`, `jsessionid`, `phpsessid` | Удаляется без `400` |
| Sensitive prefix | любое имя с префиксом `x-amz-` или `x-goog-` | Удаляется без `400` |
| Sensitive suffix | любое имя с суффиксом `_token` | Удаляется без `400` |
| Ambiguous functional | `code`, `ticket`, `sid` | Сохраняется как часть идентичности обсуждения |
| Functional/unknown | любое другое имя | Сохраняется как часть идентичности обсуждения |

Имена классифицируются после одного percent-decode, но сохранённые параметры не пересобираются через form/query parser: исходные `%20`, `+`, `%2F`, `%23`, порядок, повторы и пустые сегменты между разделителями `&` остаются без изменений. Malformed `%`, не начинающий пару hex-цифр, перед разбором детерминированно заменяется на `%25`: например, `?q=100%` становится `?q=100%25`, а `?%ZZ=x` — `?%25ZZ=x`; корректные escape-последовательности не меняются. Пустой query и `#fragment` удаляются. `data-page-url`, ручной `pageUrl` и URL текущей страницы проходят один canonicalizer; разные функциональные query остаются разными обсуждениями. Default-порты `https:443` и `http:80` удаляются из origin и идентичности страницы, остальные явные порты сохраняются. Защитный лимит 2048 символов применяется к исходному URL, включая удаляемый fragment, и к результату экранирования malformed `%`.

`code`, `ticket` и `sid` неоднозначны и functional-by-default: разные значения намеренно создают разные идентичности обсуждений. Если OAuth/SSO-интеграция использует один из них как credential, интегратор обязан передать виджету чистый canonical `pageUrl`/`data-page-url` без секрета; CloudComment не может безопасно отличить credential от функционального route state только по имени.

### Public widget surface (static)

| Surface | Contract |
| --- | --- |
| `/widget/cloud-comment-widget.js` | Public bundle served by Caddy |
| `window.CloudCommentWidget.init(options)` | Manual widget bootstrap |
| `window.CloudCommentWidget.autoInit()` | Auto bootstrap from script `data-*` attributes |

Widget options (`widget-frontend/src/widget/types.ts`):

```ts
type CloudCommentWidgetOptions = {
  siteId: string;
  apiBaseUrl?: string;
  pageUrl?: string;
  target?: string | HTMLElement;
  theme?: "auto" | "light" | "dark";
};
```

Script attributes:

- `data-site-id` — обязателен для `autoInit()`
- `data-api-base-url` — optional, default from build config
- `data-page-url` — optional, default `window.location.href`; перед API-запросом виджет удаляет fragment и tracking/sensitive query-параметры по public widget policy, сохраняя остальные параметры в исходном виде
- `data-target` — optional CSS selector or element id
- `data-theme` — optional `auto | light | dark`; default `auto`, виджет подстраивается под тему страницы

## Canonical response shapes

### Site

```json
{
  "id": "uuid",
  "ownerId": "uuid",
  "name": "example.com",
  "domain": "example.com",
  "publicKey": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "moderationMode": "PRE_MODERATION",
  "isActive": true,
  "allowedOrigins": ["https://example.com"],
  "widgetStyle": {
    "theme": "AUTO",
    "accentColor": "#0f766e",
    "cornerRadius": "MEDIUM"
  },
  "autoModeration": {
    "enabled": true,
    "strictness": "BALANCED",
    "blockedWords": ["casino", "spam"],
    "holdLinks": true,
    "blockLinks": false,
    "maxLinks": 2
  },
  "createdAt": "2026-06-28T18:00:00Z",
  "updatedAt": "2026-06-28T18:00:00Z"
}
```

`widgetStyle.theme`: `AUTO` | `LIGHT` | `DARK`; `accentColor` — hex `#RRGGBB`; `cornerRadius`: `SMALL` | `MEDIUM` | `LARGE`.

`autoModeration.strictness`: `OFF` | `RELAXED` | `BALANCED` | `STRICT`. `blockedWords` are owner-defined stop words/phrases. `holdLinks` sends suspicious link-heavy comments to moderation, `blockLinks` can mark link comments as spam, `maxLinks` sets the allowed link count.

### Политики автомодерации

Все маршруты `/api/sites/{siteId}/automoderation/**` защищены HttpOnly admin cookie и проверкой владения сайтом; изменяющие запросы дополнительно требуют CSRF. `GET /api/sites/{siteId}/automoderation/policies` возвращает единый envelope:

```json
{
  "activePolicy": {
    "id": "uuid",
    "siteId": "uuid",
    "version": 3,
    "revision": 4,
    "state": "PUBLISHED",
    "enabled": true,
    "preset": "BALANCED",
    "executionMode": "LIVE",
    "reviewThreshold": 45,
    "spamThreshold": 90,
    "cleanAction": "APPROVE",
    "linkAction": "REVIEW",
    "maxLinks": 2,
    "blockedWords": [],
    "active": true,
    "basedOnVersionId": null,
    "createdAt": "2026-07-12T10:00:00Z",
    "updatedAt": "2026-07-12T10:00:00Z",
    "publishedAt": "2026-07-12T10:00:00Z"
  },
  "draft": null,
  "versions": []
}
```

`preset`: `OPEN` | `BALANCED` | `STRICT` | `CUSTOM`; `executionMode`: `SHADOW` | `LIVE`; `state`: `DRAFT` | `PUBLISHED`; `cleanAction`: `APPROVE` | `FOLLOW_SITE_MODE`; `linkAction`: `ALLOW` | `REVIEW` | `SPAM`.

Предустановки имеют фиксированные пороги: `OPEN` — 70/130 и `APPROVE`, `BALANCED` — 45/90 и `APPROVE`, `STRICT` — 25/85 и `FOLLOW_SITE_MODE`. `CUSTOM` требует явных порогов, поведения ссылок и стоп-слов. Внешние AI-провайдеры, доверенные авторы и доверенные/заблокированные домены в этот контракт не входят.

`POST /api/sites/{siteId}/automoderation/policies` создаёт единственный mutable-черновик из preset:

```json
{
  "preset": "BALANCED",
  "enabled": true,
  "executionMode": "SHADOW"
}
```

`PATCH /api/sites/{siteId}/automoderation/policies/{policyId}` обновляет только `DRAFT`. Клиент обязан передать revision, которую он прочитал:

```json
{
  "expectedRevision": 2,
  "enabled": true,
  "preset": "CUSTOM",
  "executionMode": "SHADOW",
  "reviewThreshold": 50,
  "spamThreshold": 100,
  "cleanAction": "FOLLOW_SITE_MODE",
  "linkAction": "REVIEW",
  "maxLinks": 1,
  "blockedWords": ["стоп-слово"]
}
```

Успешное изменение увеличивает `revision`. Опубликованные строки неизменяемы. `DELETE` применяется только к текущему `DRAFT` и требует `expectedRevision` в query-параметре; устаревшая вкладка получает `409`, а не удаляет более новый черновик. Публикация требует согласовать и черновик, и ранее активную версию:

```json
{
  "expectedRevision": 3,
  "expectedActiveVersionId": "uuid-or-null"
}
```

`POST /api/sites/{siteId}/automoderation/versions/{versionId}/rollback` принимает `{ "expectedActiveVersionId": "uuid-or-null" }` и создаёт новую `PUBLISHED`-версию с новым номером и `basedOnVersionId`; выбранная старая версия не изменяется.

Ошибки lifecycle единообразны:

- `400 BAD_REQUEST` — неверный enum/UUID, пустой текст моделирования, `reviewThreshold >= spamThreshold`, превышены лимиты ссылок/стоп-слов или поля `CUSTOM` не согласованы;
- `404 NOT_FOUND` — сайт, версия, черновик или комментарий отсутствует либо принадлежит другому владельцу; чужие UUID не различаются с отсутствующими;
- `409 CONFLICT` — уже существует черновик, `expectedRevision` устарела, активная версия изменилась параллельно или операция больше не допустима для состояния версии.

#### Моделирование политики

`POST /api/sites/{siteId}/automoderation/policies/{policyId}/simulate` прогоняет тот же evaluator, который используется при создании и редактировании комментариев, но не создаёт комментарий, не меняет active/draft pointers и не сохраняет текст запроса.

Request:

```json
{
  "content": "казино ставки, быстрый заработок"
}
```

Response:

```json
{
  "score": 135,
  "decision": "SPAM",
  "baselineStatus": "PENDING",
  "effectiveStatus": "PENDING",
  "applied": false,
  "reason": "Обнаружены признаки нежелательного содержимого",
  "signals": [
    {
      "code": "SPAM_PHRASE",
      "score": 55,
      "message": "Обнаружен спам-маркер"
    }
  ]
}
```

Для `SHADOW` поле `applied=false`, а `effectiveStatus` равно базовому статусу сайта. Для `LIVE` решение применяется по следующей матрице:

| Режим сайта | `SHADOW`: любое решение | `LIVE`: `APPROVE` + `APPROVE` | `LIVE`: `APPROVE` + `FOLLOW_SITE_MODE` | `LIVE`: `REVIEW` | `LIVE`: `SPAM` |
| --- | --- | --- | --- | --- | --- |
| `PRE_MODERATION` | `PENDING` | `APPROVED` | `PENDING` | `PENDING` | `SPAM` |
| `POST_MODERATION` | `APPROVED` | `APPROVED` | `APPROVED` | `PENDING` | `SPAM` |
| `DISABLED` | `APPROVED` | `APPROVED` | `APPROVED` | `PENDING` | `SPAM` |

`POST /api/sites/{siteId}/automoderation/check` остаётся совместимым adapter для старого `Site.autoModeration`: он выполняет dry-run активной политики и не создаёт данные. Новые клиенты используют version-scoped `simulate`.

#### Обратная связь по решению

`PUT /api/moderation/comments/{commentId}/automoderation-feedback` принимает `{ "type": "FALSE_POSITIVE" }` или `{ "type": "FALSE_NEGATIVE" }`. Backend получает site, owner и policy version из связей комментария, поэтому клиент не может подменить область владения. Повторный `PUT` идемпотентно заменяет текущий тип; `DELETE` удаляет отметку. Response и постоянная запись не содержат копию текста, автора, URL страницы или сырые совпавшие фрагменты. Детальная feedback-запись хранится 90 дней.

### Public widget config

```json
{
  "siteId": "uuid",
  "moderationMode": "PRE_MODERATION",
  "style": {
    "theme": "AUTO",
    "accentColor": "#0f766e",
    "cornerRadius": "MEDIUM"
  }
}
```

### Embed code

```json
{
  "siteId": "uuid",
  "scriptUrl": "http://localhost/widget/cloud-comment-widget.js",
  "embedCode": "<script src=\"http://localhost/widget/cloud-comment-widget.js\" data-site-id=\"uuid\" data-api-base-url=\"http://localhost/api\"></script>",
  "dataAttributes": {
    "siteId": "uuid",
    "apiBaseUrl": "http://localhost/api"
  }
}
```

### Public comment

```json
{
  "id": "uuid",
  "siteId": "uuid",
  "pageId": "uuid",
  "parentId": null,
  "author": {
    "id": "uuid",
    "displayName": "User Name"
  },
  "content": "Comment text",
  "status": "APPROVED",
  "pinned": false,
  "ownedByCurrentUser": false,
  "reactions": [
    {
      "type": "LOVE",
      "emoji": "❤️",
      "label": "Нравится",
      "count": 3,
      "reactedByCurrentUser": true
    }
  ],
  "createdAt": "2026-06-28T18:00:00Z",
  "updatedAt": "2026-06-28T18:00:00Z",
  "editedAt": null,
  "replyCount": 0,
  "replies": []
}
```

Public Widget API возвращает только безопасную проекцию автора `{ id, displayName }`. Email никогда не сериализуется в публичных ответах списка, ответов, создания или редактирования комментария. Если сохранённое имя отсутствует, пустое, совпадает с email либо выглядит как email, API и виджет используют нейтральную подпись `Участник`. Moderation, owner analytics и personal-data endpoints сохраняют email в owner/self-only контуре.

### Moderation comment: сокращённый фрагмент

Owner-only moderation response использует отдельную проекцию автора и сохраняет контакт. Ниже показан только фрагмент, который иллюстрирует границу персональных данных; полный response дополнительно содержит site/page context, parent, flags, timestamps, replies и все поля `autoModeration`:

```json
{
  "id": "uuid",
  "author": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "User Name"
  },
  "content": "Comment text",
  "status": "PENDING",
  "moderationReason": "Автомодерация: требуется проверка",
  "autoModeration": {
    "decision": "REVIEW",
    "score": 55
  },
  "priority": "HIGH",
  "priorityScore": 680,
  "priorityReasons": ["Ожидает решения модератора"]
}
```

`status`: `PENDING` | `APPROVED` | `REJECTED` | `HIDDEN` | `SPAM`.

`moderationReason` nullable. Когда автомодерация усиливает решение до `PENDING` или `SPAM`, backend сохраняет короткую объяснимую причину для владельца сайта. Пользователь виджета не получает внутренние spam-сигналы: `SPAM`-отправка показывается нейтрально как "отправлено на проверку".

Ответы очереди модерации дополнительно содержат `priority`, `priorityScore` и `priorityReasons`. По умолчанию `GET /api/moderation/comments` сортируется как `sortBy=SMART&sortOrder=DESC`: backend поднимает выше комментарии с нерешенным статусом, причиной автомодерации, ссылками/контактами, длинным текстом, контекстом ответа и большим временем ожидания.

Moderation responses contain `pinned` and `favorite`. `PATCH /api/moderation/comments/{commentId}/flags` accepts nullable partial fields `{ "pinned": true, "favorite": true }`; at least one field is required. Only approved root comments can be pinned, while any owned comment can be marked favorite. Foreign and missing comment ids are masked as `404 NOT_FOUND`.

Moderation responses additionally include `parent` for reply comments. It contains the parent comment id, author summary, content, status, and creation time so the moderation card can show reply context.

### Pagination

Обёртка для list endpoints (`GET /api/sites`, `GET /api/discussions`, `GET /api/moderation/comments`, `GET /api/public/sites/{siteId}/pages/comments`):

```json
{
  "items": [],
  "page": 1,
  "pageSize": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

Query defaults: `page=1`, `pageSize=20`, max `page=100000`, max `pageSize=100`.

### Действия модерации

Запрос `POST /api/moderation/comments/{commentId}/actions`:

```json
{
  "action": "APPROVE",
  "reason": "Looks good"
}
```

`action`: `APPROVE` | `REJECT` | `HIDE` | `MARK_SPAM` | `RESTORE`.

Ответ `201`:

```json
{
  "id": "uuid",
  "commentId": "uuid",
  "action": "APPROVE",
  "fromStatus": "PENDING",
  "toStatus": "APPROVED",
  "reason": "Looks good",
  "performedBy": {
    "id": "uuid",
    "email": "moderator@example.com"
  },
  "operationId": null,
  "revertsActionId": null,
  "createdAt": "2026-06-28T18:00:00Z"
}
```

Массовый запрос `POST /api/moderation/comments/bulk-actions` принимает обязательный уникальный для пользовательской операции `operationId`, от 1 до 50 уникальных `commentIds`, прямое действие и необязательную причину. Повтор того же `operationId` для комментария возвращает ранее созданное действие и не меняет статус второй раз. Ответ `200` содержит результат для каждого идентификатора; успешные элементы не откатываются из-за ошибки другого элемента. Ошибка чужого и отсутствующего комментария имеет одинаковый безопасный вид `ACTION_FAILED`.

`POST /api/moderation/actions/{actionId}/undo` возвращает `201` и создаёт действие `UNDO` с `revertsActionId`. Отменить можно только последнее неотменённое действие над комментарием в течение 15 минут, пока статус не был изменён позднее. Повторная и конкурентная отмена запрещены ограничением базы данных.

`POST /api/moderation/operations/{operationId}/undo` возвращает `200` и результат по каждому действию массовой операции, принадлежащему сайтам текущего владельца. Каждый элемент выполняется в отдельной транзакции: допустимые комментарии восстанавливаются, а уже отменённые, изменённые позднее или вышедшие за 15-минутное окно возвращают `UNDO_CONFLICT` без отката успешных элементов. Чужая или отсутствующая операция возвращает пустой список и не раскрывает идентификаторы комментариев. Все созданные в одном запросе действия `UNDO` получают общий новый `operationId`, отличный от исходного.

`GET /api/moderation/counts` возвращает карту `statuses` со всеми статусами и суммарный `requiringDecision` для `PENDING + SPAM`.

## Smoke checklist

### Backend (automated)

Покрыто `MvpApiSmokeTests` и существующими auth/health tests:

1. `GET /api/health` → `200`, `status: "UP"`, `application: "cloud-comment"`.
2. `GET /api/auth/csrf` → `200`, HttpOnly CSRF cookie и header value; изменяющий запрос без пары → `403 INVALID_CSRF_TOKEN`.
3. `POST /api/auth/register` с CSRF → `201`; invalid input → unified error envelope.
4. `POST /api/auth/login` с CSRF → `200` with `expiresAt`, `user`, HttpOnly admin cookie и без `token`/`tokenType`.
5. `GET /api/auth/me` → `200` с cookie; без неё и с admin bearer → `401 INVALID_SESSION`.
6. `POST /api/auth/logout` с CSRF → `204`; повторный `GET /api/auth/me` → `401 INVALID_SESSION`.

### Admin SPA (manual / CI build)

6. Routes из таблицы выше присутствуют в SPA router.
7. Неавторизованный доступ к `/` перенаправляет на `/login`.
8. `npm --prefix frontend/admin run lint` и `build` проходят.

### Widget (manual / CI build)

9. Widget bundle доступен по `/widget/cloud-comment-widget.js` после `npm --prefix widget-frontend run build`.
10. `window.CloudCommentWidget.autoInit()` работает с `data-site-id`.
11. `window.CloudCommentWidget.init({ siteId, apiBaseUrl, target })` рендерит shell без ошибок.
12. `npm --prefix widget-frontend run check` и `build` проходят.

### Admin backend API (automated)

13. `POST /api/sites` создаёт сайт владельца, возвращает `siteId/publicKey` и нормализованные `allowedOrigins`.
14. `GET /api/sites/{siteId}/embed-code` возвращает snippet с `data-site-id` и `data-api-base-url`.
15. Admin site endpoints используют `/api/sites/**`, не `/api/admin/**`.
16. Owned resource access возвращает `404 NOT_FOUND` для чужих UUID.
17. `GET /api/public/sites/{siteId}/config` с разрешенным `Origin` возвращает public widget config.
18. `POST /api/public/sites/{siteId}/pages/comments` с bearer token создает comment для page.
19. `GET /api/public/sites/{siteId}/pages/comments` возвращает только видимые `APPROVED` comments.
20. Policy API покрывает DRAFT revision, publish/rollback race с `409`, owner isolation и неизменность опубликованных версий.
21. Simulation не создаёт комментарий и не меняет active/draft pointers; SHADOW сохраняет snapshot без изменения базового статуса.

### Moderation API (automated / manual smoke)

22. Moderation endpoints используют `/api/moderation/**`.
23. Response shapes comment / pagination / moderation action совпадают с разделом выше.
24. Automoderation feedback идемпотентен, owner-scoped, удаляется через 90 дней и не копирует текст комментария.

### Docs / architecture alignment

25. Route names в этом документе, C4 dynamic views и реализованных controllers совпадают по префиксу `/api/`.
26. C4 container names (`adminWebApp`, `widgetRuntime`, `backendApi`, `reverseProxy`) не расходятся с README и deployment diagram.

## C4 alignment

C4 (`docs/C4/cloud-comment.c4`) описывает те же контейнеры и route families. Dynamic views используют canonical paths из этого документа:

- `POST /api/sites`
- `GET /api/sites/{siteId}/embed-code`
- `GET /api/discussions`
- `GET /api/discussions/{rootCommentId}`
- `GET /api/moderation/comments`
- `POST /api/moderation/comments/{commentId}/actions`
- `POST /api/moderation/comments/bulk-actions`
- `POST /api/moderation/actions/{actionId}/undo`
- `POST /api/moderation/operations/{operationId}/undo`
- `GET /api/sites/{siteId}/automoderation/policies`
- `PATCH /api/sites/{siteId}/automoderation/policies/{policyId}`
- `POST /api/sites/{siteId}/automoderation/policies/{policyId}/simulate`
- `POST /api/sites/{siteId}/automoderation/policies/{policyId}/publish`
- `POST /api/sites/{siteId}/automoderation/versions/{versionId}/rollback`
- `PUT /api/moderation/comments/{commentId}/automoderation-feedback`
- `GET /api/public/sites/{siteId}/pages/comments`
- `POST /api/public/sites/{siteId}/pages/comments`

Изменения контейнеров не требуются: `adminWebApp`, `widgetRuntime`, `backendApi`, `reverseProxy`, `postgres` уже соответствуют MVP.
