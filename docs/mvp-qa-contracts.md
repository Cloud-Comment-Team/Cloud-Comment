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
| Moderation | реализовано | `/api/moderation/**`, owner-scoped queue/actions |
| Account deletion | реализовано | `/api/account/**`, email confirmation flow |
| Public widget API | реализовано | `/api/public/sites/{siteId}/**`, comments/config |
| Widget bundle / bootstrap | реализовано | public API comments + auth form |

## Auth flow

1. Клиент вызывает `POST /api/auth/login` и получает `token`, `tokenType` (`Bearer`), `expiresAt`, `user`.
2. Защищённые admin endpoints получают `Authorization: Bearer <token>`.
3. `GET /api/auth/me` возвращает текущего пользователя по bearer-токену.
4. `POST /api/auth/logout` отзывает текущую сессию и возвращает `204`.
5. Невалидный, просроченный или отозванный токен → `401` с `error.code = INVALID_SESSION`.
6. Неверные credentials → `401` с `error.code = INVALID_CREDENTIALS`.
7. Публичные endpoints помечаются `@PublicApi` и не требуют bearer-токена.

Admin frontend использует `apiClient` с `baseURL = VITE_CLOUD_COMMENT_API_BASE_URL` (обычно `http://localhost/api`) и путями `/auth/*`.

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
- Отсутствующий или невалидный bearer на защищённом endpoint → `401 INVALID_SESSION`.
- Чужой или несуществующий owned resource → `404 NOT_FOUND` с `message: "Resource not found"`.

Коды `ApiErrorCode`: `BAD_REQUEST`, `BUSINESS_ERROR`, `EMAIL_ALREADY_USED`, `INVALID_CREDENTIALS`, `INVALID_SESSION`, `INTERNAL_ERROR`, `MALFORMED_JSON`, `METHOD_NOT_ALLOWED`, `NOT_FOUND`, `TYPE_MISMATCH`, `UNSUPPORTED_MEDIA_TYPE`, `VALIDATION_FAILED`.

## Route map

Все backend routes начинаются с `/api/`. Caddy проксирует `/api/*` на backend.

### Health

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/health` | `GET` | public | Smoke check for backend availability |

### Auth

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/auth/register` | `POST` | public | Create a user account |
| `/api/auth/login` | `POST` | public | Exchange credentials for bearer token |
| `/api/auth/logout` | `POST` | self-auth bearer required | Revoke current session token |
| `/api/auth/me` | `GET` | bearer | Return current authenticated user |

### Privacy

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/privacy/consent-requirements` | `GET` | public | Current consent document versions and URLs |

Widget register uses the same request body as admin register (see below).

#### `GET /api/privacy/consent-requirements`

Response `200`:

```json
{
  "privacyPolicyVersion": "2026-07-01",
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
  "privacyPolicyVersion": "2026-07-01",
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
  "token": "opaque-session-token",
  "tokenType": "Bearer",
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

Все admin endpoints требуют `Authorization: Bearer <token>` и проверку владения через `sites.owner_id` (см. `docs/data-model.md`).

| Route | Method | Purpose |
| --- | --- | --- |
| `/api/sites` | `GET` | List sites owned by current user |
| `/api/sites` | `POST` | Create a site |
| `/api/sites/{siteId}` | `GET` | Get site details |
| `/api/sites/{siteId}` | `PATCH` | Update site name, domain, moderation mode, active flag |
| `/api/sites/{siteId}` | `DELETE` | Delete owned site and its related widget data |
| `/api/sites/{siteId}/allowed-origins` | `PUT` | Replace allowed widget origins |
| `/api/sites/{siteId}/embed-code` | `GET` | Get embed snippet for the site |
| `/api/moderation/comments` | `GET` | List comments for moderation queue (paginated) |
| `/api/moderation/comments/{commentId}` | `GET` | Get comment details |
| `/api/moderation/comments/{commentId}/actions` | `POST` | Apply moderation action |

### Account privacy API

Все account endpoints, кроме confirmation, требуют `Authorization: Bearer <token>`.

| Route | Method | Auth | Purpose |
| --- | --- | --- | --- |
| `/api/account/deletion-requests` | `POST` | bearer | Create or refresh active account deletion request and send confirmation email |
| `/api/account/deletion-requests/current` | `GET` | bearer | Return active deletion request status, if any |
| `/api/account/personal-data` | `GET` | bearer | Export current user's personal data snapshot |
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
- `pageUrl` должен быть absolute `http/https` URL того же origin, что и request `Origin`.
- Bad/missing/disallowed origin, inactive/missing site и parent comment не с этой страницы возвращают `404 NOT_FOUND` с `Resource not found`.
- Публичный список возвращает только `APPROVED`; `PENDING`, `REJECTED`, `HIDDEN`, `SPAM` не видны в виджете.
- Approved replies are returned inside the root comment `replies` array. The widget exposes reply creation only from root comments and renders replies one level deep.
- `reactions` возвращаются для корневых комментариев и ответов; при наличии bearer token публичный list помечает реакцию текущего пользователя через `reactedByCurrentUser`.
- Embedded widget auth uses site-scoped `/api/public/sites/{siteId}/auth/*` aliases, not `/api/auth/*`, so browser CORS preflight is checked against the same domain policy.
- Самообслуживание аккаунта в виджете использует site-scoped aliases `/api/public/sites/{siteId}/account/*`, а не `/api/account/*`: экспорт персональных данных и запрос удаления работают с external origin без ослабления глобального CORS.
- Создание комментария требует bearer token; guest-flow в MVP не поддерживается.

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
- `data-page-url` — optional, default `window.location.href`
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
  "createdAt": "2026-06-28T18:00:00Z",
  "updatedAt": "2026-06-28T18:00:00Z"
}
```

`widgetStyle.theme`: `AUTO` | `LIGHT` | `DARK`; `accentColor` — hex `#RRGGBB`; `cornerRadius`: `SMALL` | `MEDIUM` | `LARGE`.

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

### Comment

```json
{
  "id": "uuid",
  "siteId": "uuid",
  "pageId": "uuid",
  "parentId": null,
  "author": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "User Name"
  },
  "content": "Comment text",
  "status": "PENDING",
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
  "replies": []
}
```

`status`: `PENDING` | `APPROVED` | `REJECTED` | `HIDDEN` | `SPAM`.

Moderation responses additionally include `parent` for reply comments. It contains the parent comment id, author summary, content, status, and creation time so the moderation card can show reply context.

### Pagination

Обёртка для list endpoints (`GET /api/sites`, `GET /api/moderation/comments`, `GET /api/public/sites/{siteId}/pages/comments`):

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

### Moderation action

Request `POST /api/moderation/comments/{commentId}/actions`:

```json
{
  "action": "APPROVE",
  "reason": "Looks good"
}
```

`action`: `APPROVE` | `REJECT` | `HIDE` | `MARK_SPAM` | `RESTORE`.

Response `201`:

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
  "createdAt": "2026-06-28T18:00:00Z"
}
```

## Smoke checklist

### Backend (automated)

Покрыто `MvpApiSmokeTests` и существующими auth/health tests:

1. `GET /api/health` → `200`, `status: "UP"`, `application: "cloud-comment"`.
2. `POST /api/auth/register` → `201`; invalid input → unified error envelope.
3. `POST /api/auth/login` → `200` with `token`, `tokenType`, `expiresAt`, `user`.
4. `GET /api/auth/me` → `200` with bearer token; без токена → `401 INVALID_SESSION`.
5. `POST /api/auth/logout` → `204`; повторный `GET /api/auth/me` → `401 INVALID_SESSION`.

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

### Moderation API (automated / manual smoke)

20. Moderation endpoints используют `/api/moderation/**`.
21. Response shapes comment / pagination / moderation action совпадают с разделом выше.

### Docs / architecture alignment

22. Route names в этом документе, C4 dynamic views и реализованных controllers совпадают по префиксу `/api/`.
23. C4 container names (`adminWebApp`, `widgetRuntime`, `backendApi`, `reverseProxy`) не расходятся с README и deployment diagram.

## C4 alignment

C4 (`docs/C4/cloud-comment.c4`) описывает те же контейнеры и route families. Dynamic views используют canonical paths из этого документа:

- `POST /api/sites`
- `GET /api/sites/{siteId}/embed-code`
- `GET /api/moderation/comments`
- `POST /api/moderation/comments/{commentId}/actions`
- `GET /api/public/sites/{siteId}/pages/comments`
- `POST /api/public/sites/{siteId}/pages/comments`

Изменения контейнеров не требуются: `adminWebApp`, `widgetRuntime`, `backendApi`, `reverseProxy`, `postgres` уже соответствуют MVP.
