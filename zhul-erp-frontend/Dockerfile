# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS build
WORKDIR /app
ENV HUSKY=0 CI=true

# npm's prepare script needs the application config and source for Umi setup.
COPY . .
RUN --mount=type=cache,target=/root/.npm npm ci && npm run build

FROM nginx:1.28-alpine
RUN apk add --no-cache ca-certificates
ENV BACKEND_URL=http://backend:8080 \
    NGINX_ENVSUBST_FILTER="^BACKEND_URL$"

COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY docker/15-validate-backend.sh /docker-entrypoint.d/15-validate-backend.sh
# Keep shell entrypoints usable after a Windows checkout with CRLF conversion.
RUN sed -i 's/\r$//' /docker-entrypoint.d/15-validate-backend.sh \
    && chmod +x /docker-entrypoint.d/15-validate-backend.sh

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
