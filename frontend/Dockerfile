# Build stage: produces the static production bundle. `npm ci` (not
# `npm install`) so the build is reproducible from the committed lockfile.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage: no Node needed at all - this is a pure client-side SPA
# (no @angular/ssr, no server.ts), so a static file server is enough.
FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
