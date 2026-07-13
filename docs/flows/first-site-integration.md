# Эталонный сценарий первого сайта

## Цель

Владелец без внешней помощи проходит путь:

> Создать сайт → получить код → вставить код → CloudComment обнаружил виджет → опубликовать тестовый комментарий → увидеть его в панели → ответить владельцем.

Сценарий проверяет реальный bundle, CORS/origin policy, public API, visitor auth, комментарий, owner notification и owner reply. Синтетический комментарий из admin-панели не считается успешной интеграцией.

## Ограничения

- Один владелец, один сайт, одна внешняя страница.
- Команды, роли, guest posting, CMS-коннекторы и public realtime не входят.
- Сервер не загружает страницу клиента.
- Детальная настройка оформления и custom automod не блокируют активацию.
- Для `PRE_MODERATION` сценарий включает одно понятное одобрение перед публичным ответом. Golden path может использовать `POST_MODERATION` либо автоматическое одобрение безопасного тестового комментария.

## UX flow

### Шаг 1. Пустой аккаунт

**Экран:** «Сегодня».

**Показывается:** один onboarding-блок, краткое объяснение результата и действие «Подключить первый сайт».

**Не показывается:** нулевой график, пустое распределение реакций, нулевая очередь и другие декоративные показатели.

**Текст:**

- заголовок: «Подключите первый сайт»;
- описание: «Добавьте код CloudComment на страницу и отправьте первый комментарий. Обычно это занимает несколько минут»;
- действие: «Подключить сайт».

### Шаг 2. Создание сайта

**Экран:** `/sites/new`.

**Обязательные поля:**

1. Название сайта.
2. Адрес страницы или origin, из которого интерфейс извлекает hostname и показывает нормализованный origin.
3. Режим публикации с понятным объяснением.

Безопасный automod preset выбирается по умолчанию. Пользователь не настраивает thresholds, stop words, link actions и пятнадцать параметров оформления до первого запуска.

**Главное действие:** «Создать и получить код».

**Ошибки:** привязаны к полям; введённые значения сохраняются owner-scoped draft. Ошибка API не сбрасывает шаг.

**API:** `POST /api/sites`.

**Успех:** redirect на `/sites/{siteId}/install`.

### Шаг 3. Установка

**Экран:** `/sites/{siteId}/install`.

Показываются три последовательных состояния:

1. «Добавьте код».
2. «Ждём первый запрос».
3. «Виджет работает».

Код, разрешённый origin и кнопка копирования находятся рядом. После копирования пользователь нажимает «Код установлен, проверить».

**API:**

- `GET /api/sites/{siteId}/embed-code`;
- `GET /api/sites/{siteId}/installation-status`.

**Проверка:** bounded polling раз в две секунды не более шестидесяти секунд. Polling приостанавливается, когда вкладка скрыта, и прекращается после `HEALTHY`, `REJECTED`, ухода со страницы или timeout.

**Состояния:**

- `NEVER_SEEN` — «Запросов от виджета пока не было»;
- `HEALTHY` — «Виджет работает на example.com»;
- `STALE` — «Виджет давно не обращался к CloudComment»;
- `REJECTED` — конкретный нормализованный origin и действие «Добавить origin»;
- network error — код и инструкция остаются видимыми, доступна кнопка «Проверить снова».

Отдельное realtime-событие установки для первой версии не требуется.

### Шаг 4. Первый комментарий на внешней странице

Владелец открывает реальную страницу с установленным widget и выполняет посетительский сценарий:

1. вводит публичное имя, email и пароль либо входит;
2. пишет тестовый комментарий;
3. отправляет его;
4. видит опубликованный комментарий либо собственный статус «На проверке».

Черновик сохраняется при входе, реакции, сортировке, ошибке API и повторном render. Email не появляется в публичном DOM.

**Существующие API:**

- `POST /api/public/sites/{siteId}/auth/register`;
- `POST /api/public/sites/{siteId}/auth/login`;
- `POST /api/public/sites/{siteId}/pages/comments`.

**Необходимые изменения:** публичное имя, безопасный public author response, устойчивый draft и viewer-scoped pending lifecycle.

### Шаг 5. Комментарий появляется в панели

Событие `comment.created` создаёт одну постоянную owner notification.

Маршрут зависит от результата:

- `APPROVED` → `/discussions/{rootCommentId}`;
- `PENDING` или `SPAM` → `/moderation?comment={commentId}`;
- после одобрения → `/discussions/{rootCommentId}`.

Payload должен содержать `rootCommentId`. `parentId` не является стабильным идентификатором всей ветки.

### Шаг 6. Ответ владельца

**Экран:** `/discussions/{rootCommentId}`.

Владелец видит сайт, страницу, всю ветку и composer «Ответить как владелец сайта». Ответ отправляется owner-scoped API, а не публичным endpoint с origin страницы клиента.

**Новый API:**

- `GET /api/discussions/{rootCommentId}`;
- `POST /api/discussions/{rootCommentId}/replies`.

**Правила:**

- ownership проверяется по `site.owner_id`;
- owner reply сразу получает `APPROVED`;
- public response содержит безопасный признак `siteOwner: true` или `authorKind: OWNER`;
- email и административные роли не раскрываются;
- собственный ответ не создаёт владельцу новое уведомление;
- при ошибке текст и фокус сохраняются, доступен retry;
- после успеха ветка больше не считается ожидающей ответа владельца.

### Шаг 7. Публичная проверка

После reload/refetch внешний widget показывает ответ с текстовой меткой «Автор сайта». Public WebSocket в минимальную версию не добавляется.

## Screen inventory

| Экран | Назначение | Существующий | Изменение |
|---|---|---|---|
| Сегодня без сайтов | Начать активацию | Dashboard есть | заменить нулевую аналитику одним onboarding-блоком |
| Создание сайта | Собрать минимум данных | `/sites/new` есть | сократить шаги и убрать детальный automod/style из critical path |
| Установка | Код и диагностика | секция SiteDetail есть | отдельный адресуемый маршрут и bounded polling |
| Внешняя страница | Реальная проверка bundle/API | widget есть | исправить identity, draft, auth и pending |
| Уведомление | Привести к точному результату | есть | добавить `rootCommentId` и маршрутизацию по статусу |
| К разбору | Одобрить pending | есть | сохранить контекст и вести в discussion после решения |
| Ветка обсуждения | Прочитать и ответить | отсутствует | новый master/detail surface и owner reply API |

## Low-fidelity wireframes

### Сегодня без сайтов

```text
┌──────────────────────────────────────────────────────────────┐
│ CloudComment                                      Профиль ○  │
├───────────────┬──────────────────────────────────────────────┤
│ Сегодня       │  СЕГОДНЯ                                     │
│ Обсуждения    │                                              │
│ К разбору     │  Подключите первый сайт                      │
│ Сайты         │  Добавьте код и отправьте первый комментарий │
│ Аналитика     │                                              │
│               │  [ Подключить сайт ]                         │
│               │                                              │
│               │  Обычно это занимает несколько минут.       │
└───────────────┴──────────────────────────────────────────────┘
```

### Установка

```text
┌─ example.com / Установка ────────────────────────────────────┐
│  1 Сайт создан  ━━  2 Код установлен  ━━  3 Комментарий      │
│                                                              │
│  Добавьте код перед закрывающим </body>                       │
│  ┌──────────────────────────────────────────────┐ [Копировать]│
│  │ <script … data-site-id="…"></script>        │             │
│  └──────────────────────────────────────────────┘             │
│                                                              │
│  Разрешённая страница: https://example.com                    │
│                                                              │
│  [ Код установлен, проверить ]                                │
│                                                              │
│  ○ Ждём первый запрос…                        Проверить снова  │
└──────────────────────────────────────────────────────────────┘
```

### Ветка и ответ владельца

```text
┌─ Обсуждения ───────────────┬─ example.com / Статья ──────────┐
│ ● Ждёт ответа              │ Анна · 12:40                    │
│   «Как это подключить?»    │ Как это подключить на WordPress?│
│                            │                                 │
│   Последние ветки          │ ─────────────────────────────── │
│   …                        │ Ответить как владелец сайта     │
│                            │ ┌─────────────────────────────┐ │
│                            │ │ Напишите ответ…             │ │
│                            │ └─────────────────────────────┘ │
│                            │                  [ Ответить ]    │
└────────────────────────────┴─────────────────────────────────┘
```

На mobile список и ветка являются разными URL-экранами. Возврат восстанавливает позицию списка.

## Состояния и восстановление

| Шаг | Loading | Empty | Error | Recovery |
|---|---|---|---|---|
| Создание | блокируется только submit | не применяется | inline + общий summary | draft остаётся, первое поле ошибки получает focus |
| Embed code | skeleton фиксированной высоты | не применяется | объяснение без скрытия сайта | локальный retry |
| Detection | status с текстом и временем | `NEVER_SEEN` | `REJECTED`/network разделены | исправить origin или проверить снова |
| Widget auth | submit progress | форма входа | локализованная причина | intent и draft сохраняются |
| Comment submit | optimistic row | пустой поток | текст не очищается | retry той же операции |
| Pending | собственная строка «На проверке» | не исчезает после reload | статус недоступен | повторное получение viewer-scoped items |
| Discussion | list/detail skeleton | полезный переход к установке | сохранённый контекст не скрывается | retry конкретного блока |
| Owner reply | optimistic owner row | не применяется | draft + retry | повторная отправка с operation id |

## Тексты интерфейса

| Контекст | Текст |
|---|---|
| Site created | «Сайт создан. Теперь добавьте код на страницу» |
| Code copied | «Код скопирован» |
| Waiting | «Ждём первый запрос от виджета…» |
| Detected | «Виджет работает на example.com» |
| Rejected origin | «Запрос пришёл с https://www.example.com, но разрешён https://example.com» |
| Timeout | «Запросов пока нет. Проверьте, что код опубликован на странице, и повторите проверку» |
| Comment approved | «Комментарий опубликован» |
| Comment pending | «Комментарий отправлен на проверку. Он виден вам в этом браузере» |
| Owner composer | «Ответить как владелец сайта» |
| Reply success | «Ответ опубликован» |
| Reply error | «Ответ не отправлен. Текст сохранён — попробуйте ещё раз» |

Тексты не используют `site ID`, `public API URL`, `domain policy`, `score`, raw enum и устройство CORS, пока пользователь не откроет технические подробности.

## API gap map

| Потребность | Текущее состояние | Минимальное изменение |
|---|---|---|
| Безопасное имя | public author содержит email | `displayName` обязателен; public DTO без email |
| Canonical thread | полный `location.href` | canonical/pageKey policy и strip fragment |
| Root pagination | backend готов, client page 1 | widget state + «Показать ещё» |
| Pending после reload | public list approved-only | viewer-scoped own items/statuses |
| Список обсуждений | moderation pagination плоская | `GET /api/discussions` summaries |
| Полная ветка | public replies требуют external Origin | owner-scoped `GET /api/discussions/{id}` |
| Owner reply | отсутствует | `POST /api/discussions/{id}/replies` |
| Маркер владельца | отсутствует | `siteOwner`/`authorKind` в public DTO |
| Notification target | commentId/parentId | `rootCommentId` |
| Bulk undo | один action | operation-level undo |

Новая таблица discussions не обязательна: summary можно вычислять по comments, site owner и последней активности. Read markers и follow не входят в первую версию.

## Analytics events

События не содержат текста комментария, email, полного page URL или bearer token.

| Событие | Триггер | Поля |
|---|---|---|
| `site_setup_started` | открыта форма | session id |
| `site_created` | успешный POST site | site id, duration |
| `embed_code_copied` | copy success | site id |
| `installation_check_started` | ручная проверка | site id |
| `installation_detected` | первый `HEALTHY` | site id, elapsed seconds |
| `installation_rejected` | фактический `REJECTED` | site id, reason code |
| `first_comment_submitted` | первый create | site id, resulting status |
| `first_comment_opened_admin` | владелец открыл точный item | site id, source |
| `owner_reply_submitted` | reply success | site id, latency, retry count |
| `activation_completed` | owner reply виден public | site id, total duration |

## E2E acceptance criteria

1. Пустой аккаунт показывает одно главное действие, а не нулевую аналитику.
2. Невалидный domain/origin переводит focus на поле и не теряет draft.
3. Успешный create возвращает точный embed code и адресуемый install route.
4. `NEVER_SEEN`, `REJECTED`, исправление origin и `HEALTHY` проверяются через реальный API.
5. Polling прекращается после успеха, timeout и ухода со страницы.
6. Реальный widget загружает config, comments и visitor auth с разрешённого origin.
7. Первый комментарий создаётся из widget, а не admin fixture.
8. `PENDING` остаётся видимым автору после reload; `APPROVED` не дублируется.
9. Owner получает ровно одно постоянное уведомление и одно realtime-событие.
10. Ссылка открывает точную moderation item либо discussion thread.
11. Owner reply публикуется owner-scoped endpoint и виден public после refetch.
12. Ошибка reply сохраняет текст и focus, retry не создаёт дубль.
13. Public DOM содержит метку владельца и не содержит email.
14. Logout/login не переносит site/discussion filters другого пользователя.
15. Сценарий проходит на 390×844, 768×1024 и 1440×900, в light/dark и reduced motion.

## Accessibility criteria

- После route navigation focus находится на `h1`.
- Progress установки имеет текстовые названия шагов и `aria-current`.
- Copy success и изменение статуса сообщаются через live region.
- Ошибки полей связаны через `aria-describedby`.
- Drawer/dialog удерживает focus, закрывается Escape и возвращает focus на trigger.
- Mobile thread имеет доступную кнопку возврата.
- Метка владельца имеет текст, а не только цвет или иконку.
- Widget reaction использует `aria-pressed`.
- Axe запускается без отключения `color-contrast`.
- В обоих themes нет critical/serious violations.

## Performance criteria

- Все обязательные JS- и CSS-ресурсы Widget суммарно остаются не более 20 КБ gzip либо изменение требует отдельного измеренного решения.
- Installation polling ограничен одним запросом в две секунды и шестьюдесятью секундами.
- Discussion list загружает summaries, thread — отдельно; replies не входят в каждую строку списка.
- Realtime burst coalesce-ится в один refetch.
- Optimistic reply не пересоздаёт весь список и не вызывает layout shift.
- Шрифты не блокируют first render; используется `font-display: swap` и безопасный fallback.
- На типовом устройстве functional motion сохраняет 60 FPS.
