# Multi-stage so `docker build` on this directory is self-contained — no need
# to run `mvn package` on the host first to populate target/classes/static.
# vite.config.ts pins outDir to target/classes/static; we keep that path so
# both this Dockerfile and the maven build (frontend-maven-plugin) produce
# the identical artifact location.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/target/classes/static /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
