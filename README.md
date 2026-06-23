# Cloud-Comment

Готовый сервис комментариев, который владельцы сайтов могут подключить несколькими строками кода. Сервис предоставляет модерацию, авторизацию пользователей и централизованное управление обсуждениями.

## Локальный запуск MVP

Для локального запуска используется Docker Compose. Сейчас поднимаются Caddy, backend и PostgreSQL. Frontend, MinIO и observability stack не входят в локальный MVP, пока соответствующая функциональность не подключена в приложении.

1. Создайте локальный файл окружения:

   Windows:

   ```powershell
   copy .env.example .env
   ```

   macOS / Linux:

   ```sh
   cp .env.example .env
   ```

2. Запустите стек:

   ```sh
   docker compose up --build
   ```

3. Проверьте backend через Caddy:

   ```sh
   curl http://localhost/api/health
   ```

   Ожидаемый ответ содержит `status: "UP"` и `application: "cloud-comment"`.

Остановить контейнеры можно командой:

```sh
docker compose down
```

Для полной очистки локальной базы данных используйте:

```sh
docker compose down -v
```

## Публичный виджет

Виджет комментариев собирается как отдельный frontend bundle:

```sh
cd widget-frontend
npm install
npm run build
```

Готовый скрипт будет создан здесь:

```text
widget-frontend/dist/widget/cloud-comment-widget.js
```

Когда Caddy запущен через Docker Compose, скрипт доступен по адресу `/widget/cloud-comment-widget.js`:

```html
<script
  src="http://localhost/widget/cloud-comment-widget.js"
  data-site-id="demo-site"
  data-api-base-url="http://localhost/api"
></script>
```

Скрипт автоматически монтирует виджет после тега `script`. Ручное подключение тоже доступно:

```html
<div id="comments"></div>
<script src="http://localhost/widget/cloud-comment-widget.js"></script>
<script>
  window.CloudCommentWidget.init({
    siteId: "demo-site",
    target: "#comments",
    apiBaseUrl: "http://localhost/api"
  });
</script>
```

## Конфигурация окружений

Корневой файл `.env` используется локально для Docker Compose и обоих Vite frontend-приложений.

Для локальной разработки используйте `.env.example`:

```sh
cp .env.example .env
```

Файл `.env.production.example` служит production-like шаблоном. Production-профиль backend требует значения подключения к базе из переменных окружения и не подставляет локальные учетные данные по умолчанию.

| Переменная | Где используется | Локальное значение |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | backend | `local` |
| `SERVER_PORT` | backend | `8080` |
| `SPRING_DATASOURCE_URL` | backend | `jdbc:postgresql://localhost:5432/cloud_comment` |
| `SPRING_DATASOURCE_USERNAME` | backend | `cloud_comment` |
| `SPRING_DATASOURCE_PASSWORD` | backend | `cloud_comment` |
| `HTTP_PORT` | Docker Compose / Caddy | `80` |
| `POSTGRES_DB` | Docker Compose / PostgreSQL | `cloud_comment` |
| `POSTGRES_USER` | Docker Compose / PostgreSQL | `cloud_comment` |
| `POSTGRES_PASSWORD` | Docker Compose / PostgreSQL | `cloud_comment` |
| `VITE_CLOUD_COMMENT_API_BASE_URL` | widget и admin frontend | `http://localhost/api` |

Проверки сборки:

```sh
npm --prefix widget-frontend run check
npm --prefix widget-frontend run build
npm --prefix frontend/admin run build
```
