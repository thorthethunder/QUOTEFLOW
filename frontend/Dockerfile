# syntax=docker/dockerfile:1

# QuoteFlow frontend — multi-stage Angular build + nginx for local prod-like smoke.
# Cloudflare Pages uses the static `dist/` artifact only (this image is optional).
# Build: docker build -t quoteflow-frontend --build-arg API_BASE_URL=/api/v1 -f frontend/Dockerfile frontend

FROM node:22-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Public API base only. Same-origin `/api/v1` when nginx proxies; or absolute URL for split hosting.
ARG API_BASE_URL=/api/v1
RUN node -e "const fs=require('fs'); const p='src/environments/environment.prod.ts'; \
  let s=fs.readFileSync(p,'utf8'); \
  s=s.replace(/apiBaseUrl:\\s*'[^']*'/, \"apiBaseUrl: '${API_BASE_URL}'\"); \
  fs.writeFileSync(p,s);" \
	&& npm run build -- --configuration=production

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/run \
	&& touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid

USER nginx
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
	CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
