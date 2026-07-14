FROM node:24-alpine AS admin-build

WORKDIR /workspace/frontend/admin

COPY frontend/admin/package*.json ./
RUN npm ci

COPY frontend/admin/ ./
RUN npm run build

FROM node:24-alpine AS widget-build

ARG VITE_CLOUD_COMMENT_API_BASE_URL=http://localhost/api
ARG VITE_CLOUD_COMMENT_WIDGET_BASE_URL=http://widget.localhost
ENV VITE_CLOUD_COMMENT_API_BASE_URL=$VITE_CLOUD_COMMENT_API_BASE_URL
ENV VITE_CLOUD_COMMENT_WIDGET_BASE_URL=$VITE_CLOUD_COMMENT_WIDGET_BASE_URL

WORKDIR /workspace/widget-frontend

COPY widget-frontend/package*.json ./
RUN npm ci

COPY widget-frontend/ ./
RUN npm run build

FROM caddy:2-alpine

COPY docker/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=admin-build /workspace/frontend/admin/dist /srv/admin
COPY --from=widget-build /workspace/widget-frontend/dist/widget /srv/widget
COPY widget-frontend/demo-page.html /srv/demo/demo-page.html
