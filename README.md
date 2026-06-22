# Cloud-Comment
Готовый сервис комментариев, который владельцы сайтов могут подключить несколькими строками кода. Предоставляет модерацию, авторизацию пользователей и централизованное управление обсуждениями.

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

## Public widget bundle

The comments widget is built as a separate frontend bundle:

```sh
cd widget-frontend
npm install
npm run build
```

The generated script is written to:

```text
widget-frontend/dist/widget/cloud-comment-widget.js
```

When Caddy is running from Docker Compose, the script is served at `/widget/cloud-comment-widget.js`:

```html
<script
  src="http://localhost/widget/cloud-comment-widget.js"
  data-site-id="demo-site"
  data-api-base-url="http://localhost/api"
></script>
```

The script auto-mounts the widget after the script tag. Manual mounting is also available:

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
