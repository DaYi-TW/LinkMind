FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# Angular @angular/build outputs to dist/<project>/browser
COPY --from=build /app/dist/synapse/browser /usr/share/nginx/html
# nginx site template — listens on $PORT (Zeabur injects it); SPA fallback to index.html
RUN printf 'server {\n  listen ${PORT};\n  location / {\n    root /usr/share/nginx/html;\n    try_files $uri $uri/ /index.html;\n  }\n}\n' > /etc/nginx/default.conf.template
ENV PORT=8080
ENV API_BASE_URL=http://127.0.0.1:8000
EXPOSE 8080
# At startup: write runtime config.js from $API_BASE_URL, render nginx conf with $PORT (only), then run
CMD ["sh", "-c", "echo \"window.__APP_CONFIG__ = { apiBaseUrl: \\\"${API_BASE_URL}\\\" };\" > /usr/share/nginx/html/config.js && envsubst '$PORT' < /etc/nginx/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
