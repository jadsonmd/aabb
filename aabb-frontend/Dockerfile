# Build: roda na arquitetura da máquina que compila (o resultado é HTML/JS, igual para qualquer plataforma)
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
ENV NG_CLI_ANALYTICS=false
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

# Runtime: imagem da plataforma de destino
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/aabb-frontend/browser /usr/share/nginx/html
EXPOSE 80
