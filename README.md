# Cloud-Comment

Cloud-Comment — сервис комментариев, который владелец сайта может подключить несколькими строками кода. Проект включает backend API, публичный JS-виджет, admin frontend и локальную инфраструктуру для разработки.

## Требования

Для локального запуска через Docker нужны:

- Git;
- Docker Desktop с запущенным Docker daemon;
- свободные локальные порты `80` и `5432`.

Для локальной сборки и проверок без Docker дополнительно нужны:

- Node.js и npm для `widget-frontend` и `frontend/admin`;
- JDK 25 для backend-тестов через Gradle wrapper.

## Быстрый локальный запуск

1. Склонируйте репозиторий и перейдите в его корень.

2. Создайте локальный файл окружения:

   Windows:

   ```powershell
   copy .env.example .env
   ```

   macOS / Linux:

   ```sh
   cp .env.example .env
   ```

3. Запустите локальный стек. Docker Compose соберет backend, admin frontend и публичный widget bundle, а Caddy отдаст их одним локальным входом:

   ```sh
   docker compose up --build
   ```

4. Проверьте backend через Caddy:

   ```sh
   curl http://localhost/api/health
   ```

   Ожидаемый ответ содержит `status: "UP"` и `application: "cloud-comment"`.

Локально поднимаются:

- Caddy на `http://localhost`;
- admin frontend на `http://localhost`;
- backend внутри Docker-сети на порту `8080`;
- PostgreSQL на `127.0.0.1:5432`;
- публичный widget script на `http://localhost/widget/cloud-comment-widget.js`.

Остановить контейнеры можно командой:

```sh
docker compose down
```

Полностью очистить локальную базу данных и volume можно командой:

```sh
docker compose down -v
```

## Переменные окружения

Корневой файл `.env` используется локально для Docker Compose и обоих Vite frontend-приложений. Для разработки копируйте `.env.example` в `.env`.

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

## Backend

Backend находится в каталоге `backend/`. Для локального запуска всего стека обычно достаточно Docker Compose, но тесты можно выполнить отдельно.

Windows:

```powershell
cd backend
.\gradlew.bat test
```

macOS / Linux:

```sh
cd backend
./gradlew test
```

## Публичный виджет

Виджет комментариев собирается как отдельный frontend bundle:

```sh
npm --prefix widget-frontend install
npm --prefix widget-frontend run check
npm --prefix widget-frontend run build
```

Готовый скрипт создается здесь:

```text
widget-frontend/dist/widget/cloud-comment-widget.js
```

Когда Caddy запущен через Docker Compose, скрипт доступен по адресу:

```text
http://localhost/widget/cloud-comment-widget.js
```

Пример автоматического подключения:

```html
<script
  src="http://localhost/widget/cloud-comment-widget.js"
  data-site-id="demo-site"
  data-api-base-url="http://localhost/api"
></script>
```

Пример ручного подключения:

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

## Admin frontend

Admin frontend находится в каталоге `frontend/admin`.

```sh
npm --prefix frontend/admin install
npm --prefix frontend/admin run build
npm --prefix frontend/admin run lint
```

Локальная сборка admin frontend читает публичный URL backend API из `VITE_CLOUD_COMMENT_API_BASE_URL`.

## Полезные проверки

Проверить итоговую Docker Compose конфигурацию без запуска контейнеров:

```sh
docker compose config
```

Проверить backend API после запуска стека:

```sh
curl http://localhost/api/health
```

Проверить frontend-сборки:

```sh
npm --prefix widget-frontend run check
npm --prefix widget-frontend run build
npm --prefix frontend/admin run build
npm --prefix frontend/admin run lint
```

## Troubleshooting

Если Docker сообщает, что порт `80` занят, измените `HTTP_PORT` в `.env`, например на `8081`, и открывайте Caddy по адресу `http://localhost:8081`.

Если порт `5432` занят локальным PostgreSQL, остановите локальный сервер PostgreSQL или временно измените публикацию порта PostgreSQL в `docker-compose.yml`.

Если Docker daemon не запущен, `docker compose` не сможет собрать или поднять стек. Запустите Docker Desktop и проверьте доступность командой:

```sh
docker info
```

Если PowerShell блокирует `npm.ps1`, используйте `npm.cmd`:

```powershell
npm.cmd --prefix widget-frontend run build
npm.cmd --prefix frontend/admin run build
```

Если после изменения миграций или схемы база работает со старыми данными, очистите локальный volume:

```sh
docker compose down -v
docker compose up --build
```
